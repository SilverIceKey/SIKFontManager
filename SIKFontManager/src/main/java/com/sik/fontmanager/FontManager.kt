package com.sik.fontmanager

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.io.File
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object FontManager {

    private var defaultTypeface: Typeface? = null
    private var defaultFontWeight: FontWeightEnum = FontWeightEnum.NORMAL
    private var variableFontEnabled: Boolean = false

    private val activities = mutableListOf<WeakReference<Activity>>()

    /**
     * 避免同一个 FragmentManager 重复注册
     */
    private val registeredFragmentManagers = WeakHashMap<FragmentManager, Boolean>()

    /**
     * 记录 TextView 的字重策略：
     * - AUTO：未显式指定字重，使用全局默认字重
     * - EXPLICIT：控件自身显式指定过字重/italic，后续恢复时保留
     */
    private val textViewPolicies = WeakHashMap<TextView, TextViewFontPolicy>()

    /**
     * 回前台 / Fragment View 创建后的多轮补刷
     */
    private val resumeReapplyDelays = longArrayOf(
        0L,
        16L,
        48L,
        120L,
        260L
    )

    /**
     * Activity 级补刷任务
     */
    private val pendingActivityReapplyTasks = WeakHashMap<Activity, MutableList<Runnable>>()

    /**
     * 任意 rootView（如 Fragment root / AndroidView root）级补刷任务
     */
    private val pendingRootReapplyTasks = WeakHashMap<View, MutableList<Runnable>>()

    private enum class WeightMode {
        AUTO,
        EXPLICIT
    }

    private data class TextViewFontPolicy(
        val mode: WeightMode,
        val explicitWeight: Int? = null,
        val explicitItalic: Boolean = false,
    )

    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    fun init(context: Context) {
        try {
            val fontSource = getMetaDataString(context, "fontSource")
            val fontType =
                FontSourceTypeEnums.getFontSourceType(getMetaDataString(context, "fontType") ?: "")
            defaultFontWeight = FontWeightEnum.parse(getMetaDataString(context, "fontWeight"))
            variableFontEnabled = getMetaDataBoolean(context, "fontIsVariable")

            if (fontSource.isNullOrEmpty() || fontType == FontSourceTypeEnums.UNKNOW) {
                return
            }

            setDefaultFont(
                context = context,
                fontSource = fontSource,
                fontType = fontType,
                fontWeight = defaultFontWeight,
                isVariableFont = variableFontEnabled,
            )

            (context as? Application)?.registerActivityLifecycleCallbacks(FontLifecycleCallback())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.DONUT)
    fun setDefaultFont(
        context: Context,
        fontSource: String,
        fontType: FontSourceTypeEnums,
        fontWeight: FontWeightEnum = defaultFontWeight,
        isVariableFont: Boolean = variableFontEnabled,
    ) {
        try {
            defaultFontWeight = fontWeight
            variableFontEnabled = isVariableFont

            defaultTypeface = when (fontType) {
                FontSourceTypeEnums.ASSETS -> {
                    Typeface.createFromAsset(context.assets, fontSource)
                }

                FontSourceTypeEnums.RES -> {
                    resolveResTypeface(
                        context = context,
                        fontSource = fontSource,
                        isVariableFont = isVariableFont,
                    )
                }

                FontSourceTypeEnums.FILE -> {
                    Typeface.createFromFile(File(fontSource))
                }

                else -> null
            }

            updateAllActivities()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * RES 字体解析：
     * - variable font：fontSource 必须是 res/font 下实际资源名
     * - static font：兼容旧逻辑，支持基准名 + 后缀猜测
     */
    private fun resolveResTypeface(
        context: Context,
        fontSource: String,
        isVariableFont: Boolean,
    ): Typeface? {
        if (isVariableFont) {
            val vfId = getResId(context, fontSource)
            if (vfId != 0) {
                return ResourcesCompat.getFont(context, vfId)
            }
            return null
        }

        val directId = getResId(context, fontSource)
        if (directId != 0) {
            return ResourcesCompat.getFont(context, directId)
        }

        val candidates = listOf(
            "${fontSource}_regular",
            fontSource,
            "${fontSource}_medium",
            "${fontSource}_bold",
            "${fontSource}_semibold",
            "${fontSource}_light",
            "${fontSource}_thin",
            "${fontSource}_black",
            "${fontSource}_extralight",
            "${fontSource}_extrabold",
        )

        for (name in candidates) {
            val id = getResId(context, name)
            if (id != 0) {
                return ResourcesCompat.getFont(context, id)
            }
        }

        return null
    }

    internal fun onActivityCreated(activity: Activity) {
        val exists = activities.any { it.get() === activity }
        if (!exists) {
            activities.add(WeakReference(activity))
        }
        registerFragmentCallbacksIfNeeded(activity)
    }

    internal fun onActivityResumed(activity: Activity) {
        scheduleRecoveryReapply(activity)
    }

    internal fun onActivityPaused(activity: Activity) {
        cancelScheduledReapply(activity)
    }

    internal fun onActivityDestroyed(activity: Activity) {
        cancelScheduledReapply(activity)
        activities.removeAll { it.get() == null || it.get() === activity }
    }

    internal fun onFragmentViewCreated(root: View) {
        scheduleRecoveryReapply(root)
    }

    internal fun onFragmentResumed(root: View?) {
        root ?: return
        scheduleRecoveryReapply(root)
    }

    private fun registerFragmentCallbacksIfNeeded(activity: Activity) {
        val fragmentActivity = activity as? FragmentActivity ?: return
        val fragmentManager = fragmentActivity.supportFragmentManager

        if (registeredFragmentManagers[fragmentManager] == true) {
            return
        }

        fragmentManager.registerFragmentLifecycleCallbacks(
            FontFragmentLifecycleCallback,
            true
        )
        registeredFragmentManagers[fragmentManager] = true
    }

    private fun updateAllActivities() {
        activities.removeAll { it.get() == null }
        activities.forEach { weakRef ->
            weakRef.get()?.let { activity ->
                scheduleRecoveryReapply(activity)
            }
        }
    }

    /**
     * Activity decorView 的多轮补刷
     */
    private fun scheduleRecoveryReapply(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        val baseTypeface = defaultTypeface ?: return

        cancelScheduledReapply(activity)

        val tasks = mutableListOf<Runnable>()
        pendingActivityReapplyTasks[activity] = tasks

        resumeReapplyDelays.forEach { delayMs ->
            val task = Runnable {
                if (!isActivityAlive(activity)) return@Runnable
                applyFontToViewsInternal(decorView, baseTypeface)
            }
            tasks += task

            if (delayMs == 0L) {
                decorView.post(task)
            } else {
                decorView.postDelayed(task, delayMs)
            }
        }
    }

    /**
     * Fragment root / AndroidView root 的多轮补刷
     */
    private fun scheduleRecoveryReapply(root: View) {
        val baseTypeface = defaultTypeface ?: return

        cancelScheduledReapply(root)

        val tasks = mutableListOf<Runnable>()
        pendingRootReapplyTasks[root] = tasks

        resumeReapplyDelays.forEach { delayMs ->
            val task = Runnable {
                if (!root.isAttachedToWindowCompat()) return@Runnable
                applyFontToViewsInternal(root, baseTypeface)
            }
            tasks += task

            if (delayMs == 0L) {
                root.post(task)
            } else {
                root.postDelayed(task, delayMs)
            }
        }
    }

    private fun cancelScheduledReapply(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        pendingActivityReapplyTasks.remove(activity)?.forEach { task ->
            decorView.removeCallbacks(task)
        }
    }

    private fun cancelScheduledReapply(root: View) {
        pendingRootReapplyTasks.remove(root)?.forEach { task ->
            root.removeCallbacks(task)
        }
    }

    fun reapplyToView(root: View) {
        scheduleRecoveryReapply(root)
    }

    internal fun applyFontToViews(view: View) {
        val baseTypeface = defaultTypeface ?: return
        applyFontToViewsInternal(view, baseTypeface)
    }

    private fun applyFontToViewsInternal(
        view: View,
        baseTypeface: Typeface,
    ) {
        try {
            when (view) {
                is ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        applyFontToViewsInternal(view.getChildAt(i), baseTypeface)
                    }
                }

                is TextView -> {
                    applyFontToTextView(view, baseTypeface)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyFontToTextView(
        view: TextView,
        baseTypeface: Typeface,
    ) {
        val policy = resolveTextViewFontPolicy(view)

        val targetWeight = when (policy.mode) {
            WeightMode.AUTO -> defaultFontWeight.weight
            WeightMode.EXPLICIT -> policy.explicitWeight ?: defaultFontWeight.weight
        }

        val targetItalic = when (policy.mode) {
            WeightMode.AUTO -> view.typeface?.isItalic == true
            WeightMode.EXPLICIT -> policy.explicitItalic
        }

        val targetTypeface = applyWeight(
            baseTypeface = baseTypeface,
            fontWeight = targetWeight,
            italic = targetItalic,
        )
        view.typeface = targetTypeface
    }

    private fun resolveTextViewFontPolicy(view: TextView): TextViewFontPolicy {
        return textViewPolicies.getOrPut(view) {
            detectTextViewFontPolicy(view)
        }
    }

    /**
     * 策略：
     * - 当前 weight = 400 且非 italic：认为未显式指定，走 AUTO
     * - 当前 weight != 400 或 italic = true：认为显式指定过，走 EXPLICIT
     *
     * 这满足你当前要求：
     * - 指定了字重，就用指定的
     * - 没指定，就走全局默认
     */
    private fun detectTextViewFontPolicy(view: TextView): TextViewFontPolicy {
        val currentTypeface = view.typeface ?: return TextViewFontPolicy(
            mode = WeightMode.AUTO
        )

        val currentWeight = currentTypefaceWeight(currentTypeface)
        val currentItalic = currentTypeface.isItalic

        val hasExplicitWeight = currentWeight != FontWeightEnum.NORMAL.weight

        return if (hasExplicitWeight || currentItalic) {
            TextViewFontPolicy(
                mode = WeightMode.EXPLICIT,
                explicitWeight = currentWeight.coerceIn(1, 1000),
                explicitItalic = currentItalic,
            )
        } else {
            TextViewFontPolicy(
                mode = WeightMode.AUTO
            )
        }
    }

    private fun currentTypefaceWeight(typeface: Typeface): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            typeface.weight
        } else {
            if (typeface.isBold) 700 else 400
        }
    }

    private fun isSameTypeface(
        current: Typeface?,
        target: Typeface,
    ): Boolean {
        if (current == null) return false

        return currentTypefaceWeight(current) == currentTypefaceWeight(target) &&
                current.isItalic == target.isItalic &&
                current.style == target.style
    }

    private fun isActivityAlive(activity: Activity): Boolean {
        if (activity.isFinishing) return false
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }

    private fun View.isAttachedToWindowCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            isAttachedToWindow
        } else {
            windowToken != null
        }
    }

    /**
     * 你最低 Android 9，这里实际稳定走 API 28+ 分支
     */
    @SuppressLint("NewApi")
    private fun applyWeight(
        baseTypeface: Typeface,
        fontWeight: Int,
        italic: Boolean,
    ): Typeface {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                Typeface.create(baseTypeface, fontWeight.coerceIn(1, 1000), italic)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                baseTypeface
            }

            else -> {
                Typeface.create(
                    baseTypeface,
                    if (fontWeight >= FontWeightEnum.SEMI_BOLD.weight) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
            }
        }
    }

    private fun getMetaDataString(context: Context, key: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.get(key)?.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMetaDataBoolean(context: Context, key: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val value = appInfo.metaData?.get(key)
            when (value) {
                is Boolean -> value
                is String -> value.equals("true", ignoreCase = true)
                is Int -> value != 0
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getResId(context: Context, resName: String): Int {
        return context.resources.getIdentifier(resName, "font", context.packageName)
    }

    fun bindNewView(root: View) {
        clearTextViewPolicies(root)
        scheduleRecoveryReapply(root)
    }

    fun refreshViewPolicyAndReapply(root: View) {
        clearTextViewPolicies(root)
        scheduleRecoveryReapply(root)
    }

    private fun clearTextViewPolicies(view: View) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    clearTextViewPolicies(view.getChildAt(i))
                }
            }

            is TextView -> {
                textViewPolicies.remove(view)
            }
        }
    }

    fun getDefaultTypeface(): Typeface? = defaultTypeface

    fun getDefaultFontWeight(): FontWeightEnum = defaultFontWeight

    fun getDefaultFontWeightValue(): Int = defaultFontWeight.weight

    fun isVariableFontEnabled(): Boolean = variableFontEnabled
}
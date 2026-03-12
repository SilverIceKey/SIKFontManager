package com.sik.fontmanager

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
import java.io.File
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object FontManager {

    private var defaultTypeface: Typeface? = null
    private var defaultFontWeight: FontWeightEnum = FontWeightEnum.NORMAL
    private var variableFontEnabled: Boolean = false
    private val hasResumedOnce = WeakHashMap<Activity, Boolean>()
    private val wasPaused = WeakHashMap<Activity, Boolean>()

    private val activities = mutableListOf<WeakReference<Activity>>()

    /**
     * 回前台后的多轮补刷。
     *
     * 目的：
     * 1. 避免 onResume 时机太早，被后续 View/Fragment/三方控件恢复流程覆盖
     * 2. 对“切第三方 app -> 回来后字重掉回 thin”做无感兜底
     */
    private val resumeReapplyDelays = longArrayOf(
        0L,
        16L,
        48L,
        120L,
        260L
    )

    /**
     * 每个 Activity 当前挂着的补刷任务，防止重复叠加。
     */
    private val pendingReapplyTasks = WeakHashMap<Activity, MutableList<Runnable>>()

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
     *
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
    }

    internal fun onActivityDestroyed(activity: Activity) {
        cancelScheduledReapply(activity)
        hasResumedOnce.remove(activity)
        wasPaused.remove(activity)
        activities.removeAll { it.get() == null || it.get() === activity }
    }

    internal fun onActivityPaused(activity: Activity) {
        wasPaused[activity] = true
        cancelScheduledReapply(activity)
    }

    internal fun onActivityResumed(activity: Activity) {
        val firstResume = hasResumedOnce.put(activity, true) == null
        val resumedFromPause = wasPaused.remove(activity) == true

        when {
            // 首次进入页面：只刷一次，别上多轮补刀
            firstResume -> {
                reapplyOnce(activity)
            }

            // 真正经历过 pause -> resume：才做多轮恢复补刷
            resumedFromPause -> {
                scheduleRecoveryReapply(activity)
            }

            else -> {
                // 其他情况不做额外处理
            }
        }
    }

    private fun updateAllActivities() {
        activities.removeAll { it.get() == null }
        activities.forEach { weakRef ->
            weakRef.get()?.let { activity ->
                reapplyOnce(activity)
            }
        }
    }

    private fun reapplyOnce(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        val baseTypeface = defaultTypeface ?: return

        decorView.post {
            if (activity.isFinishing) return@post
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) {
                return@post
            }
            applyFontToViewsInternal(decorView, baseTypeface)
        }
    }

    /**
     * 无感重刷入口：
     * 回前台时不是只刷一次，而是刷多轮，尽量压住恢复时机偏晚的 View/三方控件。
     */
    private fun scheduleRecoveryReapply(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        val baseTypeface = defaultTypeface ?: return

        cancelScheduledReapply(activity)

        val tasks = mutableListOf<Runnable>()
        pendingReapplyTasks[activity] = tasks

        resumeReapplyDelays.forEach { delayMs ->
            val task = Runnable {
                // Activity 已经结束就别再折腾
                if (activity.isFinishing) return@Runnable
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) {
                    return@Runnable
                }

                // 字体被重置时，再把当前默认字重重新打进去
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

    private fun cancelScheduledReapply(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        pendingReapplyTasks.remove(activity)?.forEach { task ->
            decorView.removeCallbacks(task)
        }
    }

    fun reapplyToView(root: View) {
        val baseTypeface = defaultTypeface ?: return
        root.post {
            applyFontToViewsInternal(root, baseTypeface)
        }
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
                    view.typeface = applyWeight(
                        baseTypeface = baseTypeface,
                        fontWeight = defaultFontWeight,
                        italic = view.typeface?.isItalic == true,
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * View 侧默认字重处理策略：
     *
     * - API 28+：支持 Typeface.create(typeface, weight, italic)
     * - API 26~27：系统支持 variable font，但这里先不做伪精确控制
     * - API < 26：只能降级到 NORMAL/BOLD
     */
    private fun applyWeight(
        baseTypeface: Typeface,
        fontWeight: FontWeightEnum,
        italic: Boolean,
    ): Typeface {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                Typeface.create(baseTypeface, fontWeight.weight, italic)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                baseTypeface
            }

            else -> {
                Typeface.create(
                    baseTypeface,
                    if (fontWeight.weight >= FontWeightEnum.SEMI_BOLD.weight) {
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

    fun getDefaultTypeface(): Typeface? = defaultTypeface

    fun getDefaultFontWeight(): FontWeightEnum = defaultFontWeight

    fun getDefaultFontWeightValue(): Int = defaultFontWeight.weight

    fun isVariableFontEnabled(): Boolean = variableFontEnabled
}
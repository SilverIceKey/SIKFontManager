package com.sik.fontmanager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.lang.ref.WeakReference

object FontManager {

    private var defaultTypeface: Typeface? = null
    private var defaultFontWeight: FontWeightEnums = FontWeightEnums.NORMAL

    private val activities = mutableListOf<WeakReference<Activity>>()
    private val applyActivity: HashMap<String, Boolean?> = hashMapOf()

    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    fun init(context: Context) {
        try {
            val fontSource = getMetaData(context, "fontSource")
            val fontType =
                FontSourceTypeEnums.getFontSourceType(getMetaData(context, "fontType") ?: "")
            defaultFontWeight = FontWeightEnums.from(getMetaData(context, "fontWeight"))

            if (fontSource.isNullOrEmpty() || fontType == FontSourceTypeEnums.UNKNOW) {
                return
            }

            setDefaultFont(context, fontSource, fontType)
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
        fontWeight: FontWeightEnums = defaultFontWeight,
    ) {
        try {
            defaultFontWeight = fontWeight
            defaultTypeface = when (fontType) {
                FontSourceTypeEnums.ASSETS -> {
                    Typeface.createFromAsset(context.assets, fontSource)
                }

                FontSourceTypeEnums.RES -> {
                    resolveResTypeface(context, fontSource, fontWeight)
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
     * RES 字体解析策略：
     * 1. 先尝试按默认字重优先命中
     * 2. 再兜底回退到其它常见后缀
     * 3. 兼容旧逻辑：fontSource 直接就是完整资源名
     */
    private fun resolveResTypeface(
        context: Context,
        fontSource: String,
        fontWeight: FontWeightEnums,
    ): Typeface? {
        val directId = getResId(context, fontSource)
        if (directId != 0) {
            return ResourcesCompat.getFont(context, directId)
        }

        val candidates = buildResCandidates(fontSource, fontWeight)

        for (name in candidates) {
            val id = getResId(context, name)
            if (id != 0) {
                return ResourcesCompat.getFont(context, id)
            }
        }

        return null
    }

    private fun buildResCandidates(baseName: String, fontWeight: FontWeightEnums): List<String> {
        val preferredSuffix = when (fontWeight) {
            FontWeightEnums.THIN -> "_thin"
            FontWeightEnums.EXTRA_LIGHT -> "_extralight"
            FontWeightEnums.LIGHT -> "_light"
            FontWeightEnums.NORMAL -> "_regular"
            FontWeightEnums.MEDIUM -> "_medium"
            FontWeightEnums.SEMI_BOLD -> "_semibold"
            FontWeightEnums.BOLD -> "_bold"
            FontWeightEnums.EXTRA_BOLD -> "_extrabold"
            FontWeightEnums.BLACK -> "_black"
        }

        val fallbackSuffixes = listOf(
            preferredSuffix,
            "",
            "_regular",
            "_medium",
            "_bold",
            "_semibold",
            "_light",
            "_thin",
            "_black",
            "_extralight",
            "_extrabold",
        ).distinct()

        return fallbackSuffixes.map { suffix -> "$baseName$suffix" }
    }

    private fun updateAllActivities() {
        activities.forEach { weakRef ->
            weakRef.get()?.let { activity ->
                applyFontToViews(activity.window.decorView)
            }
        }
        activities.removeAll { it.get() == null }
    }

    private fun applyFontToViews(view: View) {
        try {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyFontToViews(view.getChildAt(i))
                }
            } else if (view is TextView) {
                defaultTypeface?.let { baseTypeface ->
                    view.typeface = applyWeight(baseTypeface, defaultFontWeight, isItalic(view))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isItalic(textView: TextView): Boolean {
        val current = textView.typeface
        return current?.isItalic == true
    }

    private fun applyWeight(
        baseTypeface: Typeface,
        fontWeight: FontWeightEnums,
        italic: Boolean,
    ): Typeface {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(baseTypeface, fontWeight.api28Weight, italic)
        } else {
            Typeface.create(baseTypeface, fontWeight.legacyStyle)
        }
    }

    private fun getMetaData(context: Context, key: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString(key)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    private fun getResId(context: Context, resName: String): Int {
        return context.resources.getIdentifier(resName, "font", context.packageName)
    }

    fun getDefaultTypeface(): Typeface? {
        return defaultTypeface
    }

    fun getDefaultFontWeight(): FontWeightEnums {
        return defaultFontWeight
    }

    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private class FontLifecycleCallback : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(WeakReference(activity))
        }

        override fun onActivityDestroyed(activity: Activity) {
            activities.removeAll { it.get() == activity || it.get() == null }
            applyActivity[activity.javaClass.simpleName] = false
        }

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            val key = activity.javaClass.simpleName
            if (defaultTypeface != null && applyActivity[key] != true) {
                applyFontToViews(activity.window.decorView)
                applyActivity[key] = true
            }
        }

        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }
}
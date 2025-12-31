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
    private val activities = mutableListOf<WeakReference<Activity>>()
    private val applyActivity: HashMap<String, Boolean?> = hashMapOf()

    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    fun init(context: Context) {
        try {
            val fontSource = getMetaData(context, "fontSource")
            val fontType =
                FontSourceTypeEnums.getFontSourceType(getMetaData(context, "fontType") ?: "")

            if (fontSource.isNullOrEmpty() || fontType == FontSourceTypeEnums.UNKNOW) {
                return
            }

            setDefaultFont(context, fontSource, fontType)
            (context as? Application)?.registerActivityLifecycleCallbacks(FontLifecycleCallback())
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
    }

    @RequiresApi(Build.VERSION_CODES.DONUT)
    fun setDefaultFont(context: Context, fontSource: String, fontType: FontSourceTypeEnums) {
        try {
            defaultTypeface = when (fontType) {
                FontSourceTypeEnums.ASSETS -> {
                    // 旧逻辑：fontSource 视为 assets 路径，例如 "fonts/myfont.ttf"
                    Typeface.createFromAsset(context.assets, fontSource)
                }

                FontSourceTypeEnums.RES -> {
                    // 新逻辑：优先兼容旧写法（直接写 res/font 名），
                    // 如果找不到，则把 fontSource 当成“基准名”，自动尝试 _thin/_bold 等后缀
                    resolveResTypeface(context, fontSource)
                }

                FontSourceTypeEnums.FILE -> {
                    // 旧逻辑：fontSource 视为完整文件路径
                    Typeface.createFromFile(File(fontSource))
                }

                else -> null
            }

            // 更新所有存储的 Activity
            updateAllActivities()
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
    }

    /**
     * 解析来自 RES 的字体：
     *
     * 支持两种用法（向下兼容）：
     *
     * 1. 旧：Manifest 里写完整 res/font 资源名（不带扩展），例如：
     *      fontSource = "myfont_bold"
     *    -> 直接用这个名字拿 resId。
     *
     * 2. 新：Manifest 里只写“基准名”，例如：
     *      fontSource = "myfont"
     *    -> 按约定自动尝试一系列后缀：
     *       myfont_regular / myfont / myfont_medium / myfont_bold / ...
     *
     * 找到的第一个就作为 View 系统的默认 Typeface。
     */
    private fun resolveResTypeface(context: Context, fontSource: String): Typeface? {
        // 1️⃣ 兼容旧逻辑：直接当成完整 font 资源名使用
        val directId = getResId(context, fontSource)
        if (directId != 0) {
            return ResourcesCompat.getFont(context, directId)
        }

        // 2️⃣ 新逻辑：fontSource 当成“基准名”，自动补后缀
        val baseName = fontSource

        // 这里是「默认优先级」（也就是 View 那边默认会选到哪个权重）
        // 可以按你喜好调整顺序，比如更偏爱 medium 就把 _medium 往前挪
        val candidates = listOf(
            "${baseName}_regular",
            baseName,                  // 有些人可能就直接叫 myfont.ttf
            "${baseName}_medium",
            "${baseName}_bold",
            "${baseName}_semibold",
            "${baseName}_light",
            "${baseName}_thin",
            "${baseName}_black",
            "${baseName}_extralight",
            "${baseName}_extrabold",
        )

        for (name in candidates) {
            val id = getResId(context, name)
            if (id != 0) {
                return ResourcesCompat.getFont(context, id)
            }
        }

        // 一个都没找到，返回 null，让上层保持默认字体
        return null
    }

    private fun updateAllActivities() {
        activities.forEach { weakRef ->
            weakRef.get()?.let { activity ->
                applyFontToViews(activity.window.decorView)
            }
        }
        // 移除已经被垃圾回收的活动
        activities.removeAll { it.get() == null }
    }

    private fun applyFontToViews(view: View) {
        try {
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyFontToViews(view.getChildAt(i))
                }
            } else if (view is TextView) {
                defaultTypeface?.let {
                    view.typeface = it
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
    }

    private fun getMetaData(context: Context, key: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
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

package com.sik.fontmanager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.lang.ref.WeakReference

object FontManager {

    private var defaultTypeface: Typeface? = null
    private val activities = mutableListOf<WeakReference<Activity>>()

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

    fun setDefaultFont(context: Context, fontSource: String, fontType: FontSourceTypeEnums) {
        try {
            defaultTypeface = when (fontType) {
                FontSourceTypeEnums.ASSETS -> Typeface.createFromAsset(context.assets, fontSource)
                FontSourceTypeEnums.RES -> ResourcesCompat.getFont(
                    context, getResId(context, fontSource)
                )

                FontSourceTypeEnums.FILE -> Typeface.createFromFile(File(fontSource))
                else -> null
            }

            // 更新所有存储的活动
            updateAllActivities()
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
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

    private class FontLifecycleCallback : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(WeakReference(activity))
        }

        override fun onActivityDestroyed(activity: Activity) {
            activities.removeAll { it.get() == activity || it.get() == null }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }
}

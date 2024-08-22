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

object FontManager {

    private var defaultTypeface: Typeface? = null

    fun init(context: Context) {
        try {
            val fontSource = getMetaData(context, "fontSource")
            val fontType = getMetaData(context, "fontType")

            if (fontSource.isNullOrEmpty() || fontType.isNullOrEmpty()) {
                return
            }

            setDefaultFont(context, fontSource, fontType)
            (context as? Application)?.registerActivityLifecycleCallbacks(FontLifecycleCallback())
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
    }

    fun setDefaultFont(context: Context, fontSource: String, fontType: String) {
        try {
            defaultTypeface = when (fontType) {
                "assets" -> Typeface.createFromAsset(context.assets, fontSource)
                "res" -> ResourcesCompat.getFont(context, getResId(context, fontSource))
                "file" -> Typeface.createFromFile(File(fontSource))
                else -> null
            }

            (context as? Application)?.let {
                it.registerActivityLifecycleCallbacks(FontLifecycleCallback())
                updateAllActivities(it)
            }
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
    }

    private fun updateAllActivities(application: Application) {
        try {
            application.registerActivityLifecycleCallbacks(object :
                Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {
                    applyFontToViews(activity.window.decorView)
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        } catch (e: Exception) {
            e.printStackTrace() // 捕获并打印异常，防止崩溃
        }
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
}

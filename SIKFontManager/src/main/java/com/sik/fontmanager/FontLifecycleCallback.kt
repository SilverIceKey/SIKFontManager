package com.sik.fontmanager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class FontLifecycleCallback : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyFontToViews(activity.window.decorView)
    }

    private fun applyFontToViews(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontToViews(view.getChildAt(i))
            }
        } else if (view is TextView) {
            FontManager.getDefaultTypeface()?.let {
                view.typeface = it
            }
        }
    }


    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

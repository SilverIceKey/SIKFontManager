package com.sik.fontmanager

import android.app.Activity
import android.app.Application
import android.os.Bundle

class FontLifecycleCallback : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        FontManager.onActivityCreated(activity)
        FontManager.applyFontToViews(activity.window.decorView)
    }

    override fun onActivityResumed(activity: Activity) {
        FontManager.onActivityResumed(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        FontManager.onActivityDestroyed(activity)
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
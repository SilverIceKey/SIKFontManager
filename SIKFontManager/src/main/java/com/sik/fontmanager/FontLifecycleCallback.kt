package com.sik.fontmanager

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal class FontLifecycleCallback : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        FontManager.onActivityCreated(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        FontManager.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        FontManager.onActivityPaused(activity)
    }

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        FontManager.onActivityDestroyed(activity)
    }
}
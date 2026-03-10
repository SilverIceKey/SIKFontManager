package com.sik.fontmanagersample

import android.app.Application
import com.sik.fontmanager.FontManager

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        FontManager.init(this)
    }
}
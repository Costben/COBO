package com.cobo.app

import android.app.Application
import com.cobo.app.platform.AndroidPlatformHolder
import com.cobo.app.platform.PlatformContext

class CoboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformHolder.initApplication(this)
        App.init(PlatformContext())
    }
}

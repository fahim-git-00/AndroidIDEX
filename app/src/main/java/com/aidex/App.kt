package com.aidex

import android.app.Application
import com.aidex.crash.CrashHandler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}

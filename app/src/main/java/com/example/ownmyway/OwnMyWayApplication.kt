package com.example.ownmyway

import android.app.Application

class OwnMyWayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applySavedTheme(this)
    }
}

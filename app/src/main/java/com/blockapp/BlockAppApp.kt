package com.blockapp

import android.app.Application
import com.blockapp.data.Prefs

class BlockAppApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}

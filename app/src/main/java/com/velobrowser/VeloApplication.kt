package com.velobrowser

import android.app.Application
import android.content.Context
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.utils.LocaleUtils
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VeloApplication : Application() {

    @Inject
    lateinit var adBlocker: AdBlocker

    override fun onCreate() {
        super.onCreate()
        instance = this
        adBlocker.initialize(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(base))
    }

    companion object {
        lateinit var instance: VeloApplication
            private set
    }
}

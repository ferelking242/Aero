package com.velobrowser

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.utils.LocaleUtils
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class VeloApplication : Application() {

    @Inject
    lateinit var adBlocker: AdBlocker

    override fun onCreate() {
        super.onCreate()
        instance = this
        setWebViewDataDirectorySuffix()
        adBlocker.initialize(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(base))
    }

    private fun setWebViewDataDirectorySuffix() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = currentProcessName()
            val suffix = when {
                processName == null -> null
                processName.endsWith(":isolated1") -> "isolated1"
                processName.endsWith(":isolated2") -> "isolated2"
                processName.endsWith(":isolated3") -> "isolated3"
                processName.endsWith(":isolated4") -> "isolated4"
                else -> null
            }
            if (suffix != null) {
                try {
                    WebView.setDataDirectorySuffix(suffix)
                } catch (e: Exception) {
                    // Already set or not supported — safe to ignore
                }
            }
        }
    }

    private fun currentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            getProcessNameLegacy()
        }
    }

    private fun getProcessNameLegacy(): String? {
        return try {
            val cmdline = File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
            if (cmdline.isNotEmpty()) cmdline else null
        } catch (e: Exception) {
            try {
                val pid = android.os.Process.myPid()
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            } catch (ex: Exception) {
                null
            }
        }
    }

    companion object {
        lateinit var instance: VeloApplication
            private set
    }
}

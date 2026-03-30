package com.velobrowser.ui.isolated

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.velobrowser.R
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.core.browser.VeloWebChromeClient
import com.velobrowser.core.browser.VeloWebViewClient
import com.velobrowser.core.browser.WebViewFactory
import com.velobrowser.core.download.VeloDownloadManager
import com.velobrowser.core.isolated.IsolatedTabManager
import com.velobrowser.data.local.datastore.BrowserSettings
import com.velobrowser.data.local.datastore.SettingsDataStore
import com.velobrowser.databinding.ActivityIsolatedBrowserBinding
import com.velobrowser.service.TabKeepAliveService
import com.velobrowser.utils.LocaleUtils
import com.velobrowser.utils.PermissionUtils
import com.velobrowser.utils.UrlUtils
import com.velobrowser.utils.gone
import com.velobrowser.utils.hideKeyboard
import com.velobrowser.utils.toast
import com.velobrowser.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class IsolatedBrowserActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_URL = "url"

        fun createIntent(context: Context, slot: Int, url: String): Intent =
            when (slot) {
                1 -> Intent(context, IsolatedSlot1Activity::class.java)
                2 -> Intent(context, IsolatedSlot2Activity::class.java)
                3 -> Intent(context, IsolatedSlot3Activity::class.java)
                4 -> Intent(context, IsolatedSlot4Activity::class.java)
                else -> Intent(context, IsolatedSlot1Activity::class.java)
            }.apply {
                putExtra(EXTRA_SLOT, slot)
                putExtra(EXTRA_URL, url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }

    protected abstract val slot: Int
    protected abstract val keepAliveServiceClass: Class<out TabKeepAliveService>

    private lateinit var binding: ActivityIsolatedBrowserBinding

    @Inject lateinit var adBlocker: AdBlocker
    @Inject lateinit var downloadManager: VeloDownloadManager
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private var webView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val loadUrlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val targetSlot = intent.getIntExtra(IsolatedTabManager.EXTRA_SLOT, -1)
            if (targetSlot == slot) {
                val url = intent.getStringExtra(IsolatedTabManager.EXTRA_URL) ?: return
                webView?.loadUrl(url)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityIsolatedBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSlotIndicator()
        setupWebView()
        setupUrlBar()
        setupNavigationControls()

        startKeepAliveService()
        registerLoadUrlReceiver()

        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        if (url.isNotEmpty()) {
            webView?.loadUrl(url)
        } else {
            activityScope.launch {
                val settings = settingsDataStore.settings.first()
                webView?.loadUrl(settings.homepage)
            }
        }

        IsolatedTabManager.saveSlotState(this, slot, url, "Isolated Tab $slot")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        if (url.isNotEmpty()) {
            webView?.loadUrl(url)
        }
    }

    private fun setupSlotIndicator() {
        binding.tvIsolatedSlotBadge.text = getString(R.string.isolated_slot_badge, slot)
    }

    private fun setupWebView() {
        val wv = WebViewFactory.create(this)

        activityScope.launch {
            val settings = settingsDataStore.settings.first()
            WebViewFactory.applySettings(wv, settings)
        }

        wv.webViewClient = VeloWebViewClient(
            adBlocker = adBlocker,
            isAdBlockerEnabled = { true },
            onPageStarted = { url ->
                runOnUiThread {
                    updateToolbar(url)
                    binding.progressBar.isVisible = true
                }
                IsolatedTabManager.saveSlotState(this, slot, url, wv.title ?: url)
                notifyUrlChanged(url, wv.title ?: url)
            },
            onPageFinished = { url, title ->
                runOnUiThread {
                    updateToolbar(url)
                    binding.progressBar.isVisible = false
                }
                IsolatedTabManager.saveSlotState(this, slot, url, title)
                notifyUrlChanged(url, title)
            },
            onError = { error ->
                runOnUiThread { toast(getString(R.string.error_loading_page, error)) }
            },
            onSslError = { _ ->
                runOnUiThread { toast(getString(R.string.error_ssl)) }
            }
        )

        wv.webChromeClient = VeloWebChromeClient(
            onProgressChanged = { progress ->
                runOnUiThread {
                    binding.progressBar.progress = progress
                    binding.progressBar.isVisible = progress in 1..99
                    binding.btnRefresh.setImageResource(
                        if (progress < 100) R.drawable.ic_stop else R.drawable.ic_refresh
                    )
                }
            },
            onTitleChanged = { title ->
                runOnUiThread {
                    binding.tvIsolatedTitle.text = title
                }
            },
            onFaviconReceived = { _ -> },
            onShowFileChooser = { _ -> false },
            onShowCustomView = { view, callback -> showFullscreen(view, callback) },
            onHideCustomView = { hideFullscreen() }
        )

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (PermissionUtils.hasStoragePermission(this)) {
                downloadManager.startDownload(this, url, userAgent, contentDisposition, mimeType)
                toast(getString(R.string.download_started))
            } else {
                requestPermissions(PermissionUtils.getRequiredPermissions(), 1002)
            }
        }

        binding.webViewContainer.addView(
            wv,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        webView = wv

        setIsolatedCookiePolicy(wv)
    }

    private fun setIsolatedCookiePolicy(wv: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, false)
        }
    }

    private fun setupUrlBar() {
        binding.urlEditText.apply {
            setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    val url = UrlUtils.processInput(
                        v.text.toString(),
                        "https://www.google.com/search?q="
                    )
                    webView?.loadUrl(url)
                    binding.root.hideKeyboard()
                    clearFocus()
                    true
                } else false
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) selectAll()
                else setText(webView?.url ?: "")
            }
        }

        binding.btnSecureIndicator.setOnClickListener {
            val url = webView?.url ?: ""
            toast(
                if (UrlUtils.isHttps(url)) getString(R.string.connection_secure)
                else getString(R.string.connection_not_secure)
            )
        }
    }

    private fun setupNavigationControls() {
        binding.btnBack.setOnClickListener {
            if (webView?.canGoBack() == true) webView?.goBack()
        }
        binding.btnForward.setOnClickListener {
            if (webView?.canGoForward() == true) webView?.goForward()
        }
        binding.btnRefresh.setOnClickListener {
            if (binding.progressBar.isVisible) webView?.stopLoading()
            else webView?.reload()
        }
        binding.btnRefresh.setOnLongClickListener {
            webView?.clearCache(true)
            webView?.reload()
            toast(getString(R.string.cache_cleared))
            true
        }
        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun updateToolbar(url: String) {
        if (!binding.urlEditText.hasFocus()) {
            binding.urlEditText.setText(url)
        }
        binding.btnSecureIndicator.setImageResource(
            if (UrlUtils.isHttps(url)) R.drawable.ic_lock else R.drawable.ic_lock_open
        )
        binding.btnBack.isEnabled = webView?.canGoBack() == true
        binding.btnForward.isEnabled = webView?.canGoForward() == true
    }

    private fun notifyUrlChanged(url: String, title: String) {
        val intent = Intent(IsolatedTabManager.ACTION_URL_CHANGED).apply {
            putExtra(IsolatedTabManager.EXTRA_SLOT, slot)
            putExtra(IsolatedTabManager.EXTRA_URL, url)
            putExtra(IsolatedTabManager.EXTRA_TITLE, title)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun registerLoadUrlReceiver() {
        val filter = IntentFilter(IsolatedTabManager.ACTION_LOAD_URL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(loadUrlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(loadUrlReceiver, filter)
        }
    }

    private fun startKeepAliveService() {
        try {
            TabKeepAliveService.createNotificationChannel(this)
            ContextCompat.startForegroundService(
                this,
                Intent(this, keepAliveServiceClass)
            )
        } catch (e: Exception) {
        }
    }

    private fun stopKeepAliveService() {
        try {
            stopService(Intent(this, keepAliveServiceClass))
        } catch (e: Exception) {
        }
    }

    private fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenView = view
        fullscreenCallback = callback
        binding.fullscreenContainer.addView(view)
        binding.fullscreenContainer.visible()
        binding.mainContent.gone()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun hideFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        binding.fullscreenContainer.removeAllViews()
        binding.fullscreenContainer.gone()
        binding.mainContent.visible()
        fullscreenView = null
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onBackPressed() {
        when {
            fullscreenView != null -> hideFullscreen()
            webView?.canGoBack() == true -> webView?.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        // INTENTIONALLY do NOT call webView.onPause() — keeps JS timers alive in background
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        // Resume rendering and timers
        webView?.onResume()
        webView?.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        // Do NOT pause WebView rendering — allow background execution
    }

    override fun onDestroy() {
        unregisterReceiver(loadUrlReceiver)
        activityScope.cancel()
        IsolatedTabManager.clearSlotState(this, slot)
        notifyTabClosed()

        binding.webViewContainer.removeAllViews()
        webView?.let { wv ->
            wv.stopLoading()
            wv.clearHistory()
            wv.destroy()
        }
        webView = null

        stopKeepAliveService()
        super.onDestroy()
    }

    private fun notifyTabClosed() {
        val intent = Intent(IsolatedTabManager.ACTION_TAB_CLOSED).apply {
            putExtra(IsolatedTabManager.EXTRA_SLOT, slot)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}

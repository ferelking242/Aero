package com.velobrowser.ui.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.velobrowser.R
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.core.browser.*
import com.velobrowser.core.download.VeloDownloadManager
import com.velobrowser.databinding.ActivityBrowserBinding
import com.velobrowser.ui.downloads.DownloadsActivity
import com.velobrowser.ui.profiles.ProfileManagerActivity
import com.velobrowser.ui.settings.SettingsActivity
import com.velobrowser.ui.tabs.TabsBottomSheet
import com.velobrowser.utils.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private val viewModel: BrowserViewModel by viewModels()

    @Inject lateinit var adBlocker: AdBlocker
    @Inject lateinit var downloadManager: VeloDownloadManager

    private var webView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsIfNeeded()
        setupWebView()
        setupUrlBar()
        setupNavigationControls()
        setupBottomBar()
        observeViewModel()

        handleIntent(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = PermissionUtils.getRequiredPermissions()
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions, 1001)
        }
    }

    private fun setupWebView() {
        val settings = viewModel.settings.value
        webView = WebViewFactory.create(this).also { wv ->
            WebViewFactory.applySettings(wv, settings)

            wv.webViewClient = VeloWebViewClient(
                adBlocker = adBlocker,
                isAdBlockerEnabled = { viewModel.settings.value.adBlockerEnabled },
                onPageStarted = { url -> viewModel.onPageStarted(url) },
                onPageFinished = { url, title -> viewModel.onPageFinished(url, title) },
                onError = { error ->
                    runOnUiThread { toast(getString(R.string.error_loading_page, error)) }
                },
                onSslError = { _ ->
                    runOnUiThread { toast(getString(R.string.error_ssl)) }
                }
            )

            wv.webChromeClient = VeloWebChromeClient(
                onProgressChanged = { progress ->
                    runOnUiThread { viewModel.onProgressChanged(progress) }
                },
                onTitleChanged = { _ -> },
                onFaviconReceived = { _ -> },
                onShowFileChooser = { _ -> false },
                onShowCustomView = { view, callback ->
                    showFullscreen(view, callback)
                },
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

            binding.webViewContainer.addView(wv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun setupUrlBar() {
        binding.urlEditText.apply {
            setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    viewModel.navigateTo(v.text.toString())
                    hideKeyboard()
                    clearFocus()
                    true
                } else false
            }

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    selectAll()
                } else {
                    setText(viewModel.currentUrl.value)
                }
            }
        }

        binding.btnSecureIndicator.setOnClickListener {
            val url = viewModel.currentUrl.value
            val isSecure = viewModel.isSecure.value
            val msg = if (isSecure) getString(R.string.connection_secure)
                      else getString(R.string.connection_not_secure)
            toast(msg)
        }
    }

    private fun setupNavigationControls() {
        binding.btnBack.setOnClickListener {
            if (webView?.canGoBack() == true) webView?.goBack()
        }
        binding.btnBack.setOnLongClickListener {
            webView?.copyBackForwardList()?.let { history ->
                toast("${history.size} pages in history")
            }
            true
        }
        binding.btnForward.setOnClickListener {
            if (webView?.canGoForward() == true) webView?.goForward()
        }
        binding.btnRefresh.setOnClickListener {
            if (viewModel.pageProgress.value < 100) {
                webView?.stopLoading()
            } else {
                webView?.reload()
            }
        }
        binding.btnRefresh.setOnLongClickListener {
            webView?.clearCache(true)
            webView?.reload()
            toast(getString(R.string.cache_cleared))
            true
        }
    }

    private fun setupBottomBar() {
        binding.btnTabs.setOnClickListener { viewModel.showTabs() }
        binding.btnHome.setOnClickListener {
            viewModel.navigateTo(viewModel.settings.value.homepage)
        }
        binding.btnBookmark.setOnClickListener { viewModel.toggleBookmark() }
        binding.btnMenu.setOnClickListener { showMenuOptions() }
    }

    private fun observeViewModel() {
        collectFlow(viewModel.loadUrlEvent) { url ->
            webView?.loadUrl(url)
        }

        collectFlow(viewModel.showTabsEvent) {
            showTabsSheet()
        }

        collectFlow(viewModel.pageProgress) { progress ->
            binding.progressBar.progress = progress
            binding.progressBar.isVisible = progress in 1..99
            binding.btnRefresh.setImageResource(
                if (progress < 100) R.drawable.ic_stop else R.drawable.ic_refresh
            )
        }

        collectFlow(viewModel.currentUrl) { url ->
            if (!binding.urlEditText.hasFocus()) {
                binding.urlEditText.setText(url)
            }
            binding.btnSecureIndicator.setImageResource(
                if (UrlUtils.isHttps(url)) R.drawable.ic_lock else R.drawable.ic_lock_open
            )
            binding.btnBack.isEnabled = webView?.canGoBack() == true
            binding.btnForward.isEnabled = webView?.canGoForward() == true
        }

        collectFlow(viewModel.isBookmarked) { bookmarked ->
            binding.btnBookmark.setImageResource(
                if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
            )
        }

        collectFlow(viewModel.tabs) { tabs ->
            binding.tvTabCount.text = tabs.size.toString()
        }

        collectFlow(viewModel.settings) { settings ->
            webView?.let { WebViewFactory.applySettings(it, settings) }
        }
    }

    private fun showTabsSheet() {
        val sheet = TabsBottomSheet.newInstance()
        sheet.show(supportFragmentManager, TabsBottomSheet.TAG)
    }

    private fun showMenuOptions() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_tab -> { viewModel.openNewTab(); true }
                R.id.menu_new_incognito -> { viewModel.openNewTab(incognito = true); true }
                R.id.menu_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                R.id.menu_profiles -> { startActivity(Intent(this, ProfileManagerActivity::class.java)); true }
                R.id.menu_downloads -> { startActivity(Intent(this, DownloadsActivity::class.java)); true }
                R.id.menu_history -> {
                    HistoryBottomSheet.newInstance().show(supportFragmentManager, HistoryBottomSheet.TAG)
                    true
                }
                R.id.menu_bookmarks -> {
                    BookmarksBottomSheet.newInstance().show(supportFragmentManager, BookmarksBottomSheet.TAG)
                    true
                }
                R.id.menu_share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.currentUrl.value)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                    true
                }
                R.id.menu_desktop_mode -> {
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenView = view
        fullscreenCallback = callback
        binding.fullscreenContainer.addView(view)
        binding.fullscreenContainer.visible()
        binding.mainContent.gone()
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
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.data?.toString()
        if (!url.isNullOrEmpty()) {
            viewModel.navigateTo(url)
        } else {
            val homepage = viewModel.settings.value.homepage
            viewModel.navigateTo(homepage)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
        webView?.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        binding.webViewContainer.removeAllViews()
        webView?.apply {
            stopLoading()
            clearHistory()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}

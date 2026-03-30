package com.velobrowser.ui.browser

import android.content.BroadcastReceiver
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
import com.velobrowser.core.isolated.IsolatedTabManager
import com.velobrowser.core.isolated.IsolatedTabReceiver
import com.velobrowser.databinding.ActivityBrowserBinding
import com.velobrowser.domain.model.BrowserTab
import com.velobrowser.ui.downloads.DownloadsActivity
import com.velobrowser.ui.isolated.IsolatedBrowserActivity
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
    @Inject lateinit var isolatedTabManager: IsolatedTabManager
    @Inject lateinit var tabManager: com.velobrowser.core.tabs.TabManager

    private val webViews = LinkedHashMap<String, WebView>()
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private lateinit var isolatedTabReceiver: IsolatedTabReceiver

    private val activeWebView: WebView?
        get() = webViews[viewModel.activeTabId.value]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsIfNeeded()
        setupUrlBar()
        setupNavigationControls()
        setupBottomBar()
        registerIsolatedTabReceiver()
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

    private fun registerIsolatedTabReceiver() {
        isolatedTabReceiver = IsolatedTabReceiver(
            tabManager = tabManager,
            isolatedTabManager = isolatedTabManager,
            onSlotUpdated = { slot, url, title ->
                viewModel.onIsolatedSlotUpdated(slot, url, title)
            },
            onSlotClosed = { slot ->
                viewModel.onIsolatedSlotClosed(slot)
            }
        )
        IsolatedTabReceiver.register(this, isolatedTabReceiver)
    }

    private fun createWebViewForTab(tab: BrowserTab): WebView {
        val tabId = tab.id
        val settings = viewModel.settings.value

        return WebViewFactory.create(this).also { wv ->
            WebViewFactory.applySettings(wv, settings)

            if (tab.isIncognito) {
                WebViewFactory.setIncognitoMode(wv)
            } else {
                WebViewFactory.setNormalMode(wv)
            }

            wv.webViewClient = VeloWebViewClient(
                adBlocker = adBlocker,
                isAdBlockerEnabled = { viewModel.settings.value.adBlockerEnabled },
                onPageStarted = { url ->
                    viewModel.onTabPageStarted(tabId, url)
                    if (viewModel.activeTabId.value == tabId) {
                        runOnUiThread { updateToolbarForUrl(url) }
                    }
                },
                onPageFinished = { url, title ->
                    viewModel.onTabPageFinished(tabId, url, title)
                    if (viewModel.activeTabId.value == tabId) {
                        runOnUiThread { updateToolbarForUrl(url) }
                    }
                },
                onError = { error ->
                    if (viewModel.activeTabId.value == tabId) {
                        runOnUiThread { toast(getString(R.string.error_loading_page, error)) }
                    }
                },
                onSslError = { _ ->
                    if (viewModel.activeTabId.value == tabId) {
                        runOnUiThread { toast(getString(R.string.error_ssl)) }
                    }
                }
            )

            wv.webChromeClient = VeloWebChromeClient(
                onProgressChanged = { progress ->
                    if (viewModel.activeTabId.value == tabId) {
                        runOnUiThread { viewModel.onProgressChanged(progress) }
                    }
                },
                onTitleChanged = { _ -> },
                onFaviconReceived = { _ -> },
                onShowFileChooser = { _ -> false },
                onShowCustomView = { view, callback ->
                    if (viewModel.activeTabId.value == tabId) showFullscreen(view, callback)
                },
                onHideCustomView = {
                    if (viewModel.activeTabId.value == tabId) hideFullscreen()
                }
            )

            wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (viewModel.activeTabId.value == tabId) {
                    if (PermissionUtils.hasStoragePermission(this)) {
                        downloadManager.startDownload(this, url, userAgent, contentDisposition, mimeType)
                        toast(getString(R.string.download_started))
                    } else {
                        requestPermissions(PermissionUtils.getRequiredPermissions(), 1002)
                    }
                }
            }

            wv.visibility = View.GONE
            binding.webViewContainer.addView(wv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private fun switchToWebView(tabId: String) {
        webViews.values.forEach { it.visibility = View.GONE }
        webViews[tabId]?.visibility = View.VISIBLE
    }

    private fun destroyWebViewForTab(tabId: String) {
        webViews.remove(tabId)?.let { wv ->
            binding.webViewContainer.removeView(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.destroy()
        }
    }

    private fun updateToolbarForUrl(url: String) {
        if (!binding.urlEditText.hasFocus()) {
            binding.urlEditText.setText(url)
        }
        binding.btnSecureIndicator.setImageResource(
            if (UrlUtils.isHttps(url)) R.drawable.ic_lock else R.drawable.ic_lock_open
        )
        binding.btnBack.isEnabled = activeWebView?.canGoBack() == true
        binding.btnForward.isEnabled = activeWebView?.canGoForward() == true
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
            val isSecure = viewModel.isSecure.value
            val msg = if (isSecure) getString(R.string.connection_secure)
                      else getString(R.string.connection_not_secure)
            toast(msg)
        }
    }

    private fun setupNavigationControls() {
        binding.btnBack.setOnClickListener {
            if (activeWebView?.canGoBack() == true) activeWebView?.goBack()
        }
        binding.btnBack.setOnLongClickListener {
            activeWebView?.copyBackForwardList()?.let { history ->
                toast("${history.size} pages in history")
            }
            true
        }
        binding.btnForward.setOnClickListener {
            if (activeWebView?.canGoForward() == true) activeWebView?.goForward()
        }
        binding.btnRefresh.setOnClickListener {
            if (viewModel.pageProgress.value < 100) {
                activeWebView?.stopLoading()
            } else {
                activeWebView?.reload()
            }
        }
        binding.btnRefresh.setOnLongClickListener {
            activeWebView?.clearCache(true)
            activeWebView?.reload()
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
            val tabId = viewModel.activeTabId.value
            if (tabId != null && !webViews.containsKey(tabId)) {
                val tab = viewModel.tabs.value.find { it.id == tabId }
                if (tab != null) {
                    val wv = createWebViewForTab(tab)
                    webViews[tabId] = wv
                    switchToWebView(tabId)
                }
            }
            activeWebView?.loadUrl(url)
        }

        collectFlow(viewModel.showTabsEvent) {
            showTabsSheet()
        }

        collectFlow(viewModel.openIsolatedTabEvent) { (slot, url) ->
            launchIsolatedTab(slot, url)
        }

        collectFlow(viewModel.activeTabId) { tabId ->
            if (tabId != null) {
                val tab = viewModel.tabs.value.find { it.id == tabId }
                if (tab != null && !tab.isIsolated) {
                    if (!webViews.containsKey(tabId)) {
                        val wv = createWebViewForTab(tab)
                        webViews[tabId] = wv
                    }
                    switchToWebView(tabId)
                    val currentUrl = tab.url
                    updateToolbarForUrl(currentUrl)
                    if (!binding.urlEditText.hasFocus()) {
                        binding.urlEditText.setText(currentUrl)
                    }
                    binding.btnSecureIndicator.setImageResource(
                        if (UrlUtils.isHttps(currentUrl)) R.drawable.ic_lock else R.drawable.ic_lock_open
                    )
                }
            }
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
            binding.btnBack.isEnabled = activeWebView?.canGoBack() == true
            binding.btnForward.isEnabled = activeWebView?.canGoForward() == true
        }

        collectFlow(viewModel.isBookmarked) { bookmarked ->
            binding.btnBookmark.setImageResource(
                if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
            )
        }

        collectFlow(viewModel.tabs) { tabs ->
            val nonIsolatedCount = tabs.count { !it.isIsolated }
            val isolatedCount = tabs.count { it.isIsolated }
            val displayCount = nonIsolatedCount + isolatedCount
            binding.tvTabCount.text = displayCount.toString()

            val currentTabIds = tabs.filter { !it.isIsolated }.map { it.id }.toSet()
            val removedIds = webViews.keys.filter { it !in currentTabIds }
            removedIds.forEach { id -> destroyWebViewForTab(id) }
        }

        collectFlow(viewModel.settings) { settings ->
            webViews.values.forEach { wv -> WebViewFactory.applySettings(wv, settings) }
        }
    }

    private fun launchIsolatedTab(slot: Int, url: String) {
        val intent = IsolatedBrowserActivity.createIntent(this, slot, url)
        startActivity(intent)
    }

    private fun showTabsSheet() {
        val sheet = TabsBottomSheet.newInstance()
        sheet.show(supportFragmentManager, TabsBottomSheet.TAG)
    }

    private fun showMenuOptions() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)

        val canOpenIsolated = viewModel.canOpenIsolatedTab()
        popup.menu.findItem(R.id.menu_new_isolated)?.isEnabled = canOpenIsolated
        popup.menu.findItem(R.id.menu_open_isolated)?.isEnabled = canOpenIsolated

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_tab -> { viewModel.openNewTab(); true }
                R.id.menu_new_incognito -> { viewModel.openNewTab(incognito = true); true }
                R.id.menu_new_isolated -> {
                    if (canOpenIsolated) {
                        viewModel.openIsolatedTab()
                    } else {
                        toast(getString(R.string.max_isolated_tabs_reached))
                    }
                    true
                }
                R.id.menu_open_isolated -> {
                    if (canOpenIsolated) {
                        viewModel.openCurrentUrlInIsolatedTab()
                    } else {
                        toast(getString(R.string.max_isolated_tabs_reached))
                    }
                    true
                }
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
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.currentUrl.value)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                    true
                }
                R.id.menu_desktop_mode -> {
                    val newValue = !viewModel.settings.value.desktopMode
                    viewModel.setDesktopMode(newValue)
                    activeWebView?.reload()
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
            activeWebView?.canGoBack() == true -> activeWebView?.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        // Only flush cookies — do NOT call webView.onPause() to keep JS timers alive
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        // Resume all WebView rendering and timers
        webViews.values.forEach { wv ->
            wv.onResume()
            wv.resumeTimers()
        }
    }

    override fun onStop() {
        super.onStop()
        // Intentionally do NOT pause WebViews — keep background execution alive
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(isolatedTabReceiver) }
        binding.webViewContainer.removeAllViews()
        webViews.values.forEach { wv ->
            wv.stopLoading()
            wv.clearHistory()
            wv.destroy()
        }
        webViews.clear()
        super.onDestroy()
    }
}

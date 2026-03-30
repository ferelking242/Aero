package com.velobrowser.ui.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.velobrowser.R
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.core.browser.*
import com.velobrowser.core.download.VeloDownloadManager
import com.velobrowser.core.isolated.IsolatedTabManager
import com.velobrowser.core.isolated.IsolatedTabReceiver
import com.velobrowser.databinding.ActivityBrowserBinding
import com.velobrowser.domain.model.BrowserTab
import com.velobrowser.ui.isolated.IsolatedBrowserActivity
import com.velobrowser.service.BrowserKeepAliveService
import com.velobrowser.ui.menu.MenuBottomSheet
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
        val canGoBack = activeWebView?.canGoBack() == true
        val canGoForward = activeWebView?.canGoForward() == true
        binding.btnBack.isEnabled = canGoBack
        binding.btnBack.alpha = if (canGoBack) 1f else 0.35f
        binding.btnForward.isEnabled = canGoForward
        binding.btnForward.alpha = if (canGoForward) 1f else 0.35f
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

        binding.btnHome.setOnLongClickListener {
            showIsolatedTabsPicker()
            true
        }

        binding.btnMenu.setOnClickListener {
            MenuBottomSheet.newInstance().show(supportFragmentManager, MenuBottomSheet.TAG)
        }
    }

    private fun showIsolatedTabsPicker() {
        val isolated = viewModel.tabs.value.filter { it.isIsolated }
        if (isolated.isEmpty()) {
            toast(getString(R.string.no_isolated_tabs))
            return
        }
        val items = isolated.map { tab ->
            val title = tab.title.ifBlank {
                tab.url.ifBlank { getString(R.string.isolated_tab_slot, tab.isolatedSlot) }
            }
            "Slot ${tab.isolatedSlot}  ·  $title"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.isolated_tabs))
            .setItems(items) { _, idx ->
                val tab = isolated[idx]
                val intent = IsolatedBrowserActivity.createIntent(this, tab.isolatedSlot, tab.url)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
            }
            .show()
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
            val canGoBack = activeWebView?.canGoBack() == true
            val canGoForward = activeWebView?.canGoForward() == true
            binding.btnBack.isEnabled = canGoBack
            binding.btnBack.alpha = if (canGoBack) 1f else 0.35f
            binding.btnForward.isEnabled = canGoForward
            binding.btnForward.alpha = if (canGoForward) 1f else 0.35f
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

    override fun onStart() {
        super.onStart()
        BrowserKeepAliveService.stop(this)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webViews.values.forEach { wv ->
            wv.onResume()
            wv.resumeTimers()
        }
    }

    override fun onStop() {
        super.onStop()
        BrowserKeepAliveService.start(this)
    }

    fun printCurrentPage() {
        val wv = activeWebView ?: return
        val pm = getSystemService(PrintManager::class.java) ?: return
        val jobName = "${getString(R.string.app_name)} — ${viewModel.currentUrl.value}"
        pm.print(jobName, wv.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
    }

    @Suppress("DEPRECATION")
    fun findInPage() {
        activeWebView?.showFindDialog(null, true)
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

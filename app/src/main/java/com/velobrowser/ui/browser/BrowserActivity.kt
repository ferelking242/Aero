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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.velobrowser.R
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.core.browser.*
import com.velobrowser.core.download.VeloDownloadManager
import com.velobrowser.databinding.ActivityBrowserBinding
import com.velobrowser.domain.model.BrowserTab
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

    private val webViews = LinkedHashMap<String, WebView>()
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val activeWebView: WebView?
        get() = webViews[viewModel.activeTabId.value]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissionsIfNeeded()
        setupUrlBar()
        setupHomeSearchBar()
        setupNavigationControls()
        setupViaMenu()
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

    // ─── Homepage show / hide ────────────────────────────────────────────────

    private fun showHomepage() {
        binding.homepageOverlay.visibility = View.VISIBLE
        binding.homeTopRight.visibility = View.VISIBLE
        binding.topToolbar.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }

    private fun hideHomepage() {
        binding.homepageOverlay.visibility = View.GONE
        binding.homeTopRight.visibility = View.GONE
        binding.topToolbar.visibility = View.VISIBLE
    }

    private fun isOnHomepage(url: String?): Boolean {
        return url.isNullOrBlank() || url == "about:blank"
    }

    // ─── Home search bar ────────────────────────────────────────────────────

    private fun setupHomeSearchBar() {
        binding.homeSearchEditText.apply {
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
        }

        binding.homeTabsBtn.setOnClickListener { viewModel.showTabs() }
        binding.homeMenuBtn.setOnClickListener { showViaMenuSheet() }
    }

    // ─── Create / switch / destroy WebViews ─────────────────────────────────

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
                        runOnUiThread {
                            updateToolbarForUrl(url)
                            if (!isOnHomepage(url)) hideHomepage()
                        }
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
        val isSecure = UrlUtils.isHttps(url)
        binding.btnSecureIndicator.setImageResource(
            if (isSecure) R.drawable.ic_lock else R.drawable.ic_globe
        )
        binding.btnBack.isEnabled = activeWebView?.canGoBack() == true
        binding.btnForward.isEnabled = activeWebView?.canGoForward() == true
    }

    // ─── URL bar ────────────────────────────────────────────────────────────

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

    // ─── Navigation controls ─────────────────────────────────────────────────

    private fun setupNavigationControls() {
        binding.btnBack.setOnClickListener {
            if (activeWebView?.canGoBack() == true) activeWebView?.goBack()
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
        binding.btnTabs.setOnClickListener { viewModel.showTabs() }
        binding.btnMenu.setOnClickListener { showViaMenuSheet() }
    }

    // ─── Via-style menu bottom sheet ─────────────────────────────────────────

    private fun setupViaMenu() {
        // Nothing to pre-setup — sheet is created on demand
    }

    private fun showViaMenuSheet() {
        val dialog = BottomSheetDialog(this, R.style.ViaMenuBottomSheetStyle)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_via_menu, null)
        dialog.setContentView(sheetView)

        val currentUrl = viewModel.currentUrl.value
        val isBookmarked = viewModel.isBookmarked.value
        val isDesktop = viewModel.settings.value.desktopMode

        // Night mode icon tint — if dark mode active, tint blue
        sheetView.findViewById<android.widget.ImageView>(R.id.iconNightMode)?.let { icon ->
            icon.setColorFilter(
                android.graphics.Color.parseColor(if (viewModel.settings.value.darkMode) "#1A73E8" else "#FFFFFF")
            )
        }

        // Desktop mode icon tint
        sheetView.findViewById<android.widget.ImageView>(R.id.iconDesktopMode)?.let { icon ->
            icon.setColorFilter(
                android.graphics.Color.parseColor(if (isDesktop) "#1A73E8" else "#FFFFFF")
            )
        }

        // Bookmark icon
        sheetView.findViewById<android.widget.ImageView>(R.id.iconAddBookmark)?.let { icon ->
            icon.setImageResource(
                if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
            )
        }

        // Row 1
        sheetView.findViewById<android.view.View>(R.id.menuNightMode)?.setOnClickListener {
            val newVal = !viewModel.settings.value.darkMode
            viewModel.setDarkMode(newVal)
            toast(if (newVal) "Dark mode on — restart to apply" else "Dark mode off — restart to apply")
            dialog.dismiss()
        }

        sheetView.findViewById<android.view.View>(R.id.menuBookmarks)?.setOnClickListener {
            dialog.dismiss()
            com.velobrowser.ui.browser.BookmarksBottomSheet.newInstance()
                .show(supportFragmentManager, com.velobrowser.ui.browser.BookmarksBottomSheet.TAG)
        }

        sheetView.findViewById<android.view.View>(R.id.menuHistory)?.setOnClickListener {
            dialog.dismiss()
            com.velobrowser.ui.browser.HistoryBottomSheet.newInstance()
                .show(supportFragmentManager, com.velobrowser.ui.browser.HistoryBottomSheet.TAG)
        }

        sheetView.findViewById<android.view.View>(R.id.menuDownloads)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, DownloadsActivity::class.java))
        }

        sheetView.findViewById<android.view.View>(R.id.menuIncognito)?.setOnClickListener {
            dialog.dismiss()
            viewModel.openNewTab(incognito = true)
        }

        // Row 2
        sheetView.findViewById<android.view.View>(R.id.menuShare)?.setOnClickListener {
            dialog.dismiss()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentUrl)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        }

        sheetView.findViewById<android.view.View>(R.id.menuAddBookmark)?.setOnClickListener {
            dialog.dismiss()
            viewModel.toggleBookmark()
            toast(if (!isBookmarked) "Bookmark added" else "Bookmark removed")
        }

        sheetView.findViewById<android.view.View>(R.id.menuDesktopMode)?.setOnClickListener {
            dialog.dismiss()
            val newVal = !isDesktop
            viewModel.setDesktopMode(newVal)
            activeWebView?.let { viewModel.settings.value.also { s ->
                WebViewFactory.applySettings(it, s.copy(desktopMode = newVal))
                it.reload()
            }}
            toast(if (newVal) "Desktop mode on" else "Desktop mode off")
        }

        sheetView.findViewById<android.view.View>(R.id.menuProfiles)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, ProfileManagerActivity::class.java))
        }

        sheetView.findViewById<android.view.View>(R.id.menuSettings)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Bottom row
        sheetView.findViewById<android.view.View>(R.id.menuNewTab)?.setOnClickListener {
            dialog.dismiss()
            viewModel.openNewTab()
        }

        sheetView.findViewById<android.view.View>(R.id.menuNewIncognito)?.setOnClickListener {
            dialog.dismiss()
            viewModel.openNewTab(incognito = true)
        }

        dialog.show()
    }

    // ─── ViewModel observations ───────────────────────────────────────────────

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
            if (!isOnHomepage(url)) hideHomepage()
            activeWebView?.loadUrl(url)
        }

        collectFlow(viewModel.showTabsEvent) {
            showTabsSheet()
        }

        collectFlow(viewModel.activeTabId) { tabId ->
            if (tabId != null) {
                val tab = viewModel.tabs.value.find { it.id == tabId }
                if (tab != null) {
                    if (!webViews.containsKey(tabId)) {
                        val wv = createWebViewForTab(tab)
                        webViews[tabId] = wv
                    }
                    switchToWebView(tabId)
                    val currentUrl = tab.url
                    if (isOnHomepage(currentUrl)) {
                        showHomepage()
                    } else {
                        hideHomepage()
                        updateToolbarForUrl(currentUrl)
                        if (!binding.urlEditText.hasFocus()) {
                            binding.urlEditText.setText(currentUrl)
                        }
                    }
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
            if (isOnHomepage(url)) {
                showHomepage()
            } else {
                hideHomepage()
                if (!binding.urlEditText.hasFocus()) {
                    binding.urlEditText.setText(url)
                }
                binding.btnSecureIndicator.setImageResource(
                    if (UrlUtils.isHttps(url)) R.drawable.ic_lock else R.drawable.ic_globe
                )
                binding.btnBack.isEnabled = activeWebView?.canGoBack() == true
                binding.btnForward.isEnabled = activeWebView?.canGoForward() == true
            }
        }

        collectFlow(viewModel.isBookmarked) { _ ->
            // bookmark state tracked in menu sheet on open
        }

        collectFlow(viewModel.tabs) { tabs ->
            val countStr = tabs.size.toString()
            binding.tvTabCount.text = countStr
            binding.homeTabCount.text = countStr

            val currentTabIds = tabs.map { it.id }.toSet()
            val removedIds = webViews.keys.filter { it !in currentTabIds }
            removedIds.forEach { id -> destroyWebViewForTab(id) }
        }

        collectFlow(viewModel.settings) { settings ->
            webViews.values.forEach { wv -> WebViewFactory.applySettings(wv, settings) }
        }
    }

    // ─── Tab sheet ───────────────────────────────────────────────────────────

    private fun showTabsSheet() {
        val sheet = TabsBottomSheet.newInstance()
        sheet.show(supportFragmentManager, TabsBottomSheet.TAG)
    }

    // ─── Fullscreen video ────────────────────────────────────────────────────

    private fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenView = view
        fullscreenCallback = callback
        binding.fullscreenContainer.addView(view)
        binding.fullscreenContainer.visibility = View.VISIBLE
        binding.mainContent.visibility = View.GONE
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun hideFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView?.let { binding.fullscreenContainer.removeView(it) }
        binding.fullscreenContainer.visibility = View.GONE
        binding.mainContent.visibility = View.VISIBLE
        fullscreenView = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    // ─── Intent handling ────────────────────────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        val url = intent?.data?.toString()
        if (!url.isNullOrEmpty()) {
            viewModel.navigateTo(url)
        } else {
            showHomepage()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onBackPressed() {
        when {
            fullscreenView != null -> hideFullscreen()
            binding.homepageOverlay.isVisible -> super.onBackPressed()
            activeWebView?.canGoBack() == true -> activeWebView?.goBack()
            else -> {
                showHomepage()
                viewModel.navigateTo("about:blank")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        webViews.values.forEach { it.onPause() }
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webViews.values.forEach { it.onResume() }
    }

    override fun onDestroy() {
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

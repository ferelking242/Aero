package com.velobrowser.core.browser

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import com.velobrowser.data.local.datastore.BrowserSettings

object WebViewFactory {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.135 Safari/537.36"

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.135 Mobile Safari/537.36"

    fun create(context: Context): WebView {
        return WebView(context).apply {
            isScrollbarFadingEnabled = true
            scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        }
    }

    fun applySettings(webView: WebView, settings: BrowserSettings) {
        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = settings.javascriptEnabled && !settings.ultraFastMode
            javaScriptCanOpenWindowsAutomatically = false

            // DOM / Storage — always on for correct functionality
            domStorageEnabled = true
            databaseEnabled = true

            // Images
            blockNetworkImage = !settings.imagesEnabled || settings.ultraFastMode
            loadsImagesAutomatically = settings.imagesEnabled && !settings.ultraFastMode

            // Cache strategy
            cacheMode = WebSettings.LOAD_DEFAULT

            // Zoom — smooth and responsive
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Security
            allowFileAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Media — require gesture to prevent auto-play abuse
            mediaPlaybackRequiresUserGesture = true

            // Encoding
            defaultTextEncodingName = "utf-8"

            // User agent
            userAgentString = if (settings.desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT

            // Safe browsing (API 26+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = settings.safeBrowsing
            }

            // Viewport — prevent tiny pages
            useWideViewPort = true
            loadWithOverviewMode = true

            // Geolocation disabled by default — privacy
            setGeolocationEnabled(false)

            // Performance: enable rendering optimisations
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                offscreenPreRaster = true
            }
        }
    }

    fun applyIncognitoSettings(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(webView, false)
        }
    }

    fun applyNormalSettings(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
    }

    // Legacy aliases for existing callers
    fun setIncognitoMode(webView: WebView) = applyIncognitoSettings(webView)
    fun setNormalMode(webView: WebView) = applyNormalSettings(webView)
}

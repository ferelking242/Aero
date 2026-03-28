package com.velobrowser.core.browser

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import com.velobrowser.data.local.datastore.BrowserSettings

object WebViewFactory {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun create(context: Context): WebView {
        return WebView(context).apply {
            isScrollbarFadingEnabled = true
            scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
            isHorizontalScrollBarEnabled = false
        }
    }

    fun applySettings(webView: WebView, settings: BrowserSettings) {
        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = settings.javascriptEnabled && !settings.ultraFastMode
            javaScriptCanOpenWindowsAutomatically = false

            // DOM / Storage
            domStorageEnabled = true
            databaseEnabled = true

            // Images
            blockNetworkImage = !settings.imagesEnabled || settings.ultraFastMode
            loadsImagesAutomatically = settings.imagesEnabled && !settings.ultraFastMode

            // Cache
            cacheMode = WebSettings.LOAD_DEFAULT

            // Zoom
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Security
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Media
            mediaPlaybackRequiresUserGesture = true

            // Text encoding
            defaultTextEncodingName = "utf-8"

            // User agent
            userAgentString = if (settings.desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT

            // Safe browsing (API 26+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = settings.safeBrowsing
            }

            // Viewport
            useWideViewPort = true
            loadWithOverviewMode = true

            // Disable geolocation
            setGeolocationEnabled(false)
        }
    }

    fun setIncognitoMode(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(webView, false)
        }
    }

    fun setNormalMode(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
    }
}

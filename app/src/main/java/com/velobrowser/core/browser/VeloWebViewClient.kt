package com.velobrowser.core.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import com.velobrowser.core.adblocker.AdBlocker

class VeloWebViewClient(
    private val adBlocker: AdBlocker,
    private val isAdBlockerEnabled: () -> Boolean,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String, String) -> Unit,
    private val onError: (String) -> Unit,
    private val onSslError: (SslError) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (isAdBlockerEnabled() && adBlocker.shouldBlock(url)) {
            return adBlocker.getBlockedResponse()
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.title ?: url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            val message = error.description?.toString() ?: "Unknown error"
            onError(message)
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Do NOT call handler.proceed() — refuse all SSL errors
        handler.cancel()
        onSslError(error)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        // Allow normal navigation
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false
        }
        // Block javascript: and file: schemes unless explicitly needed
        if (url.startsWith("javascript:") || url.startsWith("file:")) {
            return true
        }
        return false
    }
}

package com.velobrowser.core.browser

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.*

class VeloWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onFaviconReceived: (Bitmap?) -> Unit,
    private val onShowFileChooser: ((Array<Uri>?) -> Unit) -> Boolean,
    private val onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    private val onHideCustomView: () -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        super.onReceivedTitle(view, title)
        onTitleChanged(title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        super.onReceivedIcon(view, icon)
        onFaviconReceived(icon)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        return onShowFileChooser { uris ->
            filePathCallback.onReceiveValue(uris)
        }
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        super.onShowCustomView(view, callback)
        onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        onHideCustomView()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        callback.invoke(origin, false, false)
    }
}

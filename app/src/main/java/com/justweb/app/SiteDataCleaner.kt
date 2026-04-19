package com.justweb.app

import android.webkit.WebView

enum class SiteCleanupMode {
    LOGIN_STATE,
    WEB_CACHE
}

class SiteDataCleaner {

    fun clearUsing(app: WebApp, mode: SiteCleanupMode, webView: WebView?, onResult: (Boolean) -> Unit) {
        webView?.clearCache(true)
        webView?.clearHistory()
        webView?.clearFormData()
        val usedNativeDeletion = WebViewProfileManager.clearBrowsingData(
            appId = app.id,
            siteUrl = app.url,
            mode = mode,
            onComplete = { onResult(true) }
        )
        if (!usedNativeDeletion) {
            onResult(false)
        }
    }
}

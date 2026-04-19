package com.justweb.app

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.ValueCallback
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object WebViewProfileManager {

    fun profileNameFor(appId: String): String = "justweb_$appId"

    fun supportsPerAppProfiles(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }

    fun supportsPreciseDataDeletion(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
    }

    fun prepareWebViewForApp(webView: android.webkit.WebView, appId: String) {
        if (!supportsPerAppProfiles()) return
        val profileName = profileNameFor(appId)
        getOrCreateProfile(profileName)
        WebViewCompat.setProfile(webView, profileName)
    }

    fun cookieManagerForApp(appId: String): CookieManager {
        return if (supportsPerAppProfiles()) {
            getOrCreateProfile(profileNameFor(appId)).cookieManager
        } else {
            CookieManager.getInstance()
        }
    }

    fun webStorageForApp(appId: String): WebStorage {
        return if (supportsPerAppProfiles()) {
            getOrCreateProfile(profileNameFor(appId)).webStorage
        } else {
            WebStorage.getInstance()
        }
    }

    fun geolocationPermissionsForApp(appId: String): GeolocationPermissions? {
        if (!supportsPerAppProfiles()) return null
        return getOrCreateProfile(profileNameFor(appId)).geolocationPermissions
    }

    fun cookieHeaderForApp(appId: String?, url: String): String? {
        return if (!appId.isNullOrBlank()) {
            cookieManagerForApp(appId).getCookie(url)
        } else {
            CookieManager.getInstance().getCookie(url)
        }
    }

    fun clearBrowsingData(appId: String, siteUrl: String, mode: SiteCleanupMode, onComplete: () -> Unit): Boolean {
        if (supportsPerAppProfiles()) {
            val profile = getOrCreateProfile(profileNameFor(appId))
            when (mode) {
                SiteCleanupMode.LOGIN_STATE,
                SiteCleanupMode.WEB_CACHE -> clearProfileData(profile, onComplete)
            }
            return true
        }

        if (supportsPreciseDataDeletion()) {
            val site = runCatching { java.net.URL(siteUrl) }
                .getOrNull()
                ?.host
                ?.takeIf { it.isNotBlank() }
                ?: return false
            WebStorageCompat.deleteBrowsingDataForSite(WebStorage.getInstance(), site, onComplete)
            return true
        }

        val cookieManager = CookieManager.getInstance()
        return runCatching {
            WebStorage.getInstance().deleteAllData()
            cookieManager.removeAllCookies(ValueCallback {
                cookieManager.flush()
                onComplete()
            })
        }.isSuccess
    }

    private fun clearProfileData(profile: androidx.webkit.Profile, onComplete: () -> Unit) {
        if (supportsPreciseDataDeletion()) {
            WebStorageCompat.deleteBrowsingData(profile.webStorage, onComplete)
            return
        }

        profile.geolocationPermissions.clearAll()
        profile.webStorage.deleteAllData()
        profile.cookieManager.removeAllCookies(ValueCallback {
            profile.cookieManager.flush()
            onComplete()
        })
    }

    private fun getOrCreateProfile(profileName: String): androidx.webkit.Profile {
        return runOnMainThreadBlocking {
            ProfileStore.getInstance().getOrCreateProfile(profileName)
        }
    }

    private fun <T> runOnMainThreadBlocking(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }

        val result = AtomicReference<Result<T>>()
        val latch = CountDownLatch(1)
        mainHandler.post {
            result.set(runCatching(block))
            latch.countDown()
        }

        check(latch.await(MAIN_THREAD_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            "Timed out waiting for WebView profile access"
        }

        return result.get().getOrThrow()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private const val MAIN_THREAD_TIMEOUT_SEC = 5L
}

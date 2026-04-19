package com.justweb.app

import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.ValueCallback
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

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
        ProfileStore.getInstance().getOrCreateProfile(profileName)
        WebViewCompat.setProfile(webView, profileName)
    }

    fun cookieManagerForApp(appId: String): CookieManager {
        return if (supportsPerAppProfiles()) {
            ProfileStore.getInstance().getOrCreateProfile(profileNameFor(appId)).cookieManager
        } else {
            CookieManager.getInstance()
        }
    }

    fun webStorageForApp(appId: String): WebStorage {
        return if (supportsPerAppProfiles()) {
            ProfileStore.getInstance().getOrCreateProfile(profileNameFor(appId)).webStorage
        } else {
            WebStorage.getInstance()
        }
    }

    fun geolocationPermissionsForApp(appId: String): GeolocationPermissions? {
        if (!supportsPerAppProfiles()) return null
        return ProfileStore.getInstance().getOrCreateProfile(profileNameFor(appId)).geolocationPermissions
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
            val profile = ProfileStore.getInstance().getOrCreateProfile(profileNameFor(appId))
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
}

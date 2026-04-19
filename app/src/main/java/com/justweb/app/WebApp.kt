package com.justweb.app

import java.util.UUID

data class WebApp(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val fullscreen: Boolean = true,
    val showStatusBar: Boolean = false,
    val showAddressBar: Boolean = false,
    val notificationsEnabled: Boolean = false
)

data class UrlValidation(
    val isValid: Boolean,
    val normalizedUrl: String,
    val error: String? = null
)

object UrlValidator {
    fun validate(input: String): UrlValidation {
        if (input.isBlank()) {
            return UrlValidation(false, "", "请输入网站地址")
        }

        var url = input.trim()

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        return try {
            val parsed = java.net.URL(url)
            val host = parsed.host
            if (host.isNullOrBlank() || host.length < 2) {
                return UrlValidation(false, "", "无效的网址")
            }
            if (!host.contains(".")) {
                if (host != "localhost" && !host.matches(Regex("^[a-zA-Z0-9]+$"))) {
                    return UrlValidation(false, "", "无效的网址格式")
                }
            }
            UrlValidation(true, url)
        } catch (e: Exception) {
            UrlValidation(false, "", "无效的网址")
        }
    }
}
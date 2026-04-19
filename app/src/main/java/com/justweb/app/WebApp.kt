package com.justweb.app

import java.util.UUID

data class WebApp(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val fullscreen: Boolean = true,
    val keepScreenOn: Boolean = false,
    val desktopMode: Boolean = false,
    val externalLinkPolicy: Int = ExternalLinkPolicy.SAME_DOMAIN
)

object ExternalLinkPolicy {
    const val SAME_DOMAIN = 0
    const val IN_APP = 1
    const val SYSTEM = 2

    data class Option(
        val value: Int,
        val label: String,
        val description: String
    )

    val options = listOf(
        Option(SAME_DOMAIN, "站内打开，外链交系统", "适合大多数网站工具，站内继续留在应用里。"),
        Option(IN_APP, "所有网页都在应用内打开", "适合希望完全留在 JustWeb 内部的网站。"),
        Option(SYSTEM, "网页链接都交给系统", "适合只把当前网站首页当入口使用。")
    )

    fun optionFor(value: Int): Option {
        return options.firstOrNull { it.value == value } ?: options.first()
    }

    fun valueForLabel(label: String): Int {
        return options.firstOrNull { it.label == label }?.value ?: SAME_DOMAIN
    }
}

data class UrlValidation(
    val isValid: Boolean,
    val normalizedUrl: String,
    val error: String? = null
)

object UrlValidator {
    private val ipv4Regex = Regex("""^(\\d{1,3}\\.){3}\\d{1,3}$""")
    private val localHostnameRegex = Regex("""^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$""")
    private val supportedSchemeRegex = Regex("""^https?://.*$""", RegexOption.IGNORE_CASE)
    private val anySchemeRegex = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:.*$""")

    fun validate(input: String): UrlValidation {
        if (input.isBlank()) {
            return UrlValidation(false, "", "请输入网站地址")
        }

        var url = input.trim()

        if (anySchemeRegex.matches(url) && !supportedSchemeRegex.matches(url)) {
            return UrlValidation(false, "", "网址只能使用 http:// 或 https:// 开头")
        }

        if (!supportedSchemeRegex.matches(url)) {
            url = "https://$url"
        }

        return try {
            val parsed = java.net.URL(url)
            val host = parsed.host.orEmpty()
            if (!isSupportedHost(host)) {
                return UrlValidation(false, "", "请输入有效的网址")
            }
            UrlValidation(true, url)
        } catch (e: Exception) {
            UrlValidation(false, "", "无效的网址")
        }
    }

    private fun isSupportedHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) {
            return true
        }

        if (ipv4Regex.matches(host)) {
            return host.split('.').all { segment ->
                val number = segment.toIntOrNull()
                number != null && number in 0..255
            }
        }

        if (host.contains('.')) {
            return true
        }

        return localHostnameRegex.matches(host)
    }
}

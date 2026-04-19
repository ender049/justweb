package com.justweb.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import com.caverock.androidsvg.SVG
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class SiteIconStore(private val context: Context) {

    private val iconDirectory = File(context.filesDir, "site_icons").apply { mkdirs() }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(appId: String): Bitmap? {
        val iconFile = iconFileFor(appId)
        if (!iconFile.exists()) return null
        return BitmapFactory.decodeFile(iconFile.absolutePath)
    }

    fun delete(appId: String) {
        iconFileFor(appId).delete()
    }

    fun fetchAndStore(app: WebApp, forceRefresh: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        if (!forceRefresh && load(app.id) != null) {
            onResult?.invoke(true)
            return
        }

        if (onResult != null) {
            pendingCallbacks.computeIfAbsent(app.id) { CopyOnWriteArrayList() }.add(onResult)
        }

        if (!inFlight.add(app.id)) {
            return
        }

        executor.execute {
            val bitmap = runCatching {
                resolveSiteIcon(app.url, WebViewProfileManager.cookieHeaderForApp(app.id, app.url))
            }.getOrNull()
            val success = bitmap?.let {
                save(app.id, bitmap)
                true
            } ?: false

            inFlight.remove(app.id)
            val callbacks = pendingCallbacks.remove(app.id).orEmpty()
            mainHandler.post {
                callbacks.forEach { callback -> callback(success) }
            }
        }
    }

    fun fetchPreview(siteUrl: String, appId: String? = null, onResult: (Bitmap?) -> Unit) {
        executor.execute {
            val bitmap = runCatching {
                resolveSiteIcon(siteUrl, WebViewProfileManager.cookieHeaderForApp(appId, siteUrl))
            }.getOrNull()
            mainHandler.post { onResult(bitmap) }
        }
    }

    fun store(appId: String, bitmap: Bitmap) {
        save(appId, bitmap)
    }

    private fun resolveSiteIcon(pageUrl: String, cookieHeader: String?): Bitmap? {
        val pageResponse = fetchBytes(pageUrl, cookieHeader, accept = HTML_ACCEPT) ?: return null
        val pageDocument = Jsoup.parse(String(pageResponse.bytes), pageResponse.url)

        val candidates = LinkedHashSet<IconCandidate>()
        collectManifestIcons(pageDocument, cookieHeader, candidates)
        collectLinkIcons(pageDocument, pageResponse.url, candidates)

        return candidates
            .sortedByDescending { it.score }
            .firstNotNullOfOrNull { loadIconBitmap(it.url, cookieHeader) }
    }

    private fun collectManifestIcons(
        document: Document,
        cookieHeader: String?,
        candidates: MutableSet<IconCandidate>
    ) {
        val manifestHref = document.select("link[rel~=manifest]").firstOrNull()?.absUrl("href").orEmpty()
        if (manifestHref.isBlank()) return

        val manifestResponse = fetchBytes(manifestHref, cookieHeader, accept = MANIFEST_ACCEPT) ?: return
        val manifest = runCatching { JSONObject(String(manifestResponse.bytes)) }.getOrNull() ?: return
        val icons = manifest.optJSONArray("icons") ?: return

        for (index in 0 until icons.length()) {
            val item = icons.optJSONObject(index) ?: continue
            val src = item.optString("src").takeIf { it.isNotBlank() } ?: continue
            val purpose = item.optString("purpose")
            if (purpose.contains("maskable", ignoreCase = true)) {
                candidates += IconCandidate(resolveUrl(manifestResponse.url, src), scoreIcon(item.optString("sizes")) + 500)
            }
            candidates += IconCandidate(resolveUrl(manifestResponse.url, src), scoreIcon(item.optString("sizes")))
        }
    }

    private fun collectLinkIcons(document: Document, pageUrl: String, candidates: MutableSet<IconCandidate>) {
        document.select("link[rel]").forEach { node ->
            val rel = node.attr("rel")
            if (!rel.contains("icon", ignoreCase = true) && !rel.contains("apple-touch-icon", ignoreCase = true)) {
                return@forEach
            }

            val href = node.absUrl("href").ifBlank { resolveUrl(pageUrl, node.attr("href")) }
            if (href.isBlank()) return@forEach
            val sizes = node.attr("sizes")
            candidates += IconCandidate(href, scoreIcon(sizes))
        }
    }

    private fun loadIconBitmap(iconUrl: String, cookieHeader: String?): Bitmap? {
        val response = fetchBytes(iconUrl, cookieHeader, accept = IMAGE_ACCEPT) ?: return null
        val contentType = response.contentType.orEmpty().lowercase()
        val bytes = response.bytes

        return when {
            contentType.contains("svg") || iconUrl.substringAfterLast('.', "").equals("svg", ignoreCase = true) -> {
                decodeSvg(bytes)
            }

            else -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    private fun decodeSvg(bytes: ByteArray): Bitmap? {
        return runCatching {
            val svg = SVG.getFromString(String(bytes))
            svg.setDocumentWidth(TARGET_ICON_SIZE.toFloat())
            svg.setDocumentHeight(TARGET_ICON_SIZE.toFloat())
            val picture = svg.renderToPicture()
            Bitmap.createBitmap(TARGET_ICON_SIZE, TARGET_ICON_SIZE, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawPicture(picture)
            }
        }.getOrNull()
    }

    private fun fetchBytes(url: String, cookieHeader: String?, accept: String): FetchResult? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", USER_AGENT)
                if (!cookieHeader.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
            }

            val body = connection.inputStream.use { it.readBytes() }
            FetchResult(
                url = connection.url.toString(),
                bytes = body,
                contentType = connection.contentType
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun save(appId: String, bitmap: Bitmap) {
        FileOutputStream(iconFileFor(appId)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }
    }

    private fun iconFileFor(appId: String): File {
        return File(iconDirectory, "$appId.png")
    }

    private fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        return runCatching { URL(URL(baseUrl), relativeOrAbsolute).toString() }.getOrDefault(relativeOrAbsolute)
    }

    private fun scoreIcon(sizes: String?): Int {
        val normalized = sizes.orEmpty().trim()
        if (normalized.equals("any", ignoreCase = true)) return 100_000
        return normalized
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                val parts = token.lowercase().split('x')
                if (parts.size != 2) return@mapNotNull null
                val width = parts[0].toIntOrNull() ?: return@mapNotNull null
                val height = parts[1].toIntOrNull() ?: return@mapNotNull null
                width * height
            }
            .maxOrNull()
            ?: 0
    }

    private data class FetchResult(
        val url: String,
        val bytes: ByteArray,
        val contentType: String?
    )

    private data class IconCandidate(
        val url: String,
        val score: Int
    )

    companion object {
        private val executor = Executors.newFixedThreadPool(3)
        private val inFlight = ConcurrentHashMap.newKeySet<String>()
        private val pendingCallbacks = ConcurrentHashMap<String, CopyOnWriteArrayList<(Boolean) -> Unit>>()
        private const val TARGET_ICON_SIZE = 192
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val MANIFEST_ACCEPT = "application/manifest+json,application/json,text/plain;q=0.8,*/*;q=0.5"
        private const val IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    }
}

package com.justweb.app

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.progressindicator.LinearProgressIndicator

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var webViewContainer: ViewGroup
    private lateinit var progressBar: LinearProgressIndicator
    private var app: WebApp? = null
    private var appBaseUri: Uri? = null
    private lateinit var defaultUserAgent: String
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var popupWebView: WebView? = null
    private var pendingPermissionAction: PendingPermissionAction? = null
    private var pendingBlobDownload: PendingBlobDownload? = null
    private val siteDataCleaner = SiteDataCleaner()

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        fileChooserCallback = null
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }

    private val blobDownloadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pendingDownload = pendingBlobDownload ?: return@registerForActivityResult
        pendingBlobDownload = null

        val targetUri = result.data?.data
        if (result.resultCode != RESULT_OK || targetUri == null) {
            Toast.makeText(this, "已取消保存下载文件", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        try {
            contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(pendingDownload.bytes)
                output.flush()
            } ?: throw IllegalStateException("无法写入文件")
            Toast.makeText(this, "已保存到所选位置", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存下载文件失败", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(RequestMultiplePermissions()) { grants ->
        when (val action = pendingPermissionAction) {
            is PendingPermissionAction.Geolocation -> {
                val granted = grants[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
                action.callback.invoke(action.origin, granted, false)
            }

            is PendingPermissionAction.WebResources -> {
                val granted = action.androidPermissions.all { permission -> grants[permission] == true }
                if (granted) {
                    action.request.grant(action.webResources)
                } else {
                    action.request.deny()
                }
            }

            null -> Unit
        }
        pendingPermissionAction = null
    }

    companion object {
        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_FORCE_RELOAD = "force_reload"
        private const val KEY_WEBVIEW_STATE = "webview_state"
        private val EMBEDDED_SCHEMES = setOf("about", "data", "file", "javascript")
    }

    private sealed interface PendingPermissionAction {
        data class Geolocation(
            val origin: String,
            val callback: GeolocationPermissions.Callback
        ) : PendingPermissionAction

        data class WebResources(
            val request: PermissionRequest,
            val webResources: Array<String>,
            val androidPermissions: List<String>
        ) : PendingPermissionAction
    }

    private data class PendingBlobDownload(
        val bytes: ByteArray,
        val mimeType: String,
        val fileName: String
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_web_view)
        webViewContainer = findViewById(R.id.webViewContainer)
        progressBar = findViewById(R.id.progressBar)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        if (!loadAppFromIntent(intent, savedInstanceState, reloadIfSameApp = false)) {
            return
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(appId: String): WebView {
        return WebView(this).apply webViewApply@ {
            WebViewProfileManager.prepareWebViewForApp(this, appId)
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                allowContentAccess = true
                allowFileAccess = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(true)
                setSupportMultipleWindows(true)
                builtInZoomControls = true
                displayZoomControls = false
            }

            defaultUserAgent = settings.userAgentString.orEmpty()
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webViewApply, true)
            }
            addJavascriptInterface(BlobDownloadBridge(), "JustWebBlobDownloader")
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                handleDownloadRequest(url, userAgent, contentDisposition, mimeType)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    return if (shouldOpenInApp(uri)) {
                        false
                    } else {
                        openExternalUri(uri)
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    applyWindowMode()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@WebViewActivity, "页面加载失败，请检查网址或网络", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    return launchFileChooser(filePathCallback, fileChooserParams)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request ?: return
                    handleWebPermissionRequest(request)
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                    if ((pendingPermissionAction as? PendingPermissionAction.WebResources)?.request == request) {
                        pendingPermissionAction = null
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                    if (origin.isNullOrBlank() || callback == null) {
                        callback?.invoke(origin, false, false)
                        return
                    }
                    handleGeolocationPermission(origin, callback)
                }

                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                    return createPopupWindow(resultMsg)
                }

                override fun onCloseWindow(window: WebView?) {
                    destroyPopupWebView()
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    this@WebViewActivity.title = title?.takeIf { it.isNotBlank() } ?: app?.name
                }
            }
        }
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        val allowedWebResources = mutableListOf<String>()
        val androidPermissions = linkedSetOf<String>()

        request.resources.forEach { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    allowedWebResources += resource
                    androidPermissions += android.Manifest.permission.RECORD_AUDIO
                }

                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    allowedWebResources += resource
                    androidPermissions += android.Manifest.permission.CAMERA
                }

                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                    allowedWebResources += resource
                }
            }
        }

        if (allowedWebResources.isEmpty()) {
            request.deny()
            return
        }

        val missingPermissions = androidPermissions.filterNot(::hasPermission)
        if (missingPermissions.isEmpty()) {
            request.grant(allowedWebResources.toTypedArray())
            return
        }

        pendingPermissionAction = PendingPermissionAction.WebResources(
            request = request,
            webResources = allowedWebResources.toTypedArray(),
            androidPermissions = missingPermissions
        )
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun handleGeolocationPermission(origin: String, callback: GeolocationPermissions.Callback) {
        app?.id?.let { appId ->
            WebViewProfileManager.geolocationPermissionsForApp(appId)?.allow(origin)
        }

        val missingPermissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ).filterNot(::hasPermission)

        if (missingPermissions.isEmpty()) {
            callback.invoke(origin, true, false)
            return
        }

        pendingPermissionAction = PendingPermissionAction.Geolocation(origin, callback)
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadAppFromIntent(intent: Intent, savedInstanceState: Bundle?, reloadIfSameApp: Boolean): Boolean {
        val appId = intent.getStringExtra(EXTRA_APP_ID)
        if (appId.isNullOrEmpty()) {
            finish()
            return false
        }

        val resolvedApp = WebAppStorage(this).getById(appId)
        if (resolvedApp == null) {
            Toast.makeText(this, "应用不存在", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        val currentAppId = app?.id
        val forceReload = intent.getBooleanExtra(EXTRA_FORCE_RELOAD, false)

        if (!this::webView.isInitialized || currentAppId != resolvedApp.id) {
            recreateWebView(resolvedApp.id)
        }

        app = resolvedApp
        appBaseUri = Uri.parse(resolvedApp.url)
        title = resolvedApp.name
        applySiteSettings(resolvedApp)
        applyWindowMode()

        val webViewState = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)
        if (webViewState != null && !forceReload) {
            webView.restoreState(webViewState)
            return true
        }

        if (!reloadIfSameApp && !forceReload && currentAppId == resolvedApp.id) {
            return true
        }

        destroyPopupWebView()
        webView.stopLoading()
        webView.clearHistory()
        webView.loadUrl(resolvedApp.url)
        return true
    }

    private fun recreateWebView(appId: String) {
        if (this::webView.isInitialized) {
            destroyCurrentWebView()
        }

        webView = createConfiguredWebView(appId)
        webViewContainer.removeAllViews()
        webViewContainer.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun launchFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = filePathCallback

        val chooserIntent = try {
            fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        } catch (e: Exception) {
            fileChooserCallback = null
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            fileChooserLauncher.launch(chooserIntent)
            true
        } catch (e: ActivityNotFoundException) {
            fileChooserCallback = null
            Toast.makeText(this, "设备上没有可用的文件选择器", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun createPopupWindow(resultMsg: Message?): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        destroyPopupWebView()

        val popupView = WebView(this).apply {
            app?.id?.let { WebViewProfileManager.prepareWebViewForApp(this, it) }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                handleDownloadRequest(url, userAgent, contentDisposition, mimeType)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    return routePopupUri(uri)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val popupUrl = url?.takeUnless { it == "about:blank" } ?: return
                    routePopupUri(Uri.parse(popupUrl))
                }
            }
        }

        popupWebView = popupView
        transport.webView = popupView
        resultMsg.sendToTarget()
        return true
    }

    private fun routePopupUri(uri: Uri): Boolean {
        if (uri.toString() == "about:blank") {
            return false
        }

        destroyPopupWebView()
        return if (shouldOpenInApp(uri)) {
            webView.loadUrl(uri.toString())
            true
        } else {
            openExternalUri(uri)
        }
    }

    private fun shouldOpenInApp(uri: Uri): Boolean {
        val config = app ?: return false
        val scheme = uri.scheme?.lowercase().orEmpty()
        return when (scheme) {
            "http", "https" -> when (config.externalLinkPolicy) {
                ExternalLinkPolicy.IN_APP -> true
                ExternalLinkPolicy.SYSTEM -> false
                else -> isInternalWebHost(uri.host)
            }
            in EMBEDDED_SCHEMES -> true
            else -> false
        }
    }

    private fun isInternalWebHost(host: String?): Boolean {
        val targetHost = host?.lowercase()?.trim().orEmpty()
        val appHost = appBaseUri?.host?.lowercase()?.trim().orEmpty()
        if (targetHost.isBlank() || appHost.isBlank()) {
            return false
        }
        return areRelatedHosts(appHost, targetHost)
    }

    private fun areRelatedHosts(first: String, second: String): Boolean {
        if (first == second) {
            return true
        }

        val firstHasDomainStructure = first.contains('.')
        val secondHasDomainStructure = second.contains('.')
        if (!firstHasDomainStructure || !secondHasDomainStructure) {
            return false
        }

        return first.endsWith(".$second") || second.endsWith(".$first")
    }

    private fun destroyPopupWebView() {
        popupWebView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        popupWebView = null
    }

    private fun handleDownloadRequest(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val downloadUrl = url?.takeIf { it.isNotBlank() }
        if (downloadUrl.isNullOrEmpty()) {
            Toast.makeText(this, "无法开始下载", Toast.LENGTH_SHORT).show()
            return
        }

        if (downloadUrl.startsWith("blob:", ignoreCase = true)) {
            startBlobDownload(downloadUrl, contentDisposition, mimeType)
            return
        }

        val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            Toast.makeText(this, "当前设备不支持下载管理", Toast.LENGTH_SHORT).show()
            return
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle(fileName)
            setDescription(app?.name ?: "JustWeb")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            if (!mimeType.isNullOrBlank()) {
                setMimeType(mimeType)
            }

            val cookies = WebViewProfileManager.cookieHeaderForApp(app?.id, downloadUrl)
            if (!cookies.isNullOrBlank()) {
                addRequestHeader("Cookie", cookies)
            }
            if (!userAgent.isNullOrBlank()) {
                addRequestHeader("User-Agent", userAgent)
            }
            webView.url?.takeIf { it.isNotBlank() }?.let { referer ->
                addRequestHeader("Referer", referer)
            }
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        try {
            downloadManager.enqueue(request)
            Toast.makeText(this, "已开始下载", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySiteSettings(config: WebApp) {
        webView.settings.apply {
            userAgentString = if (config.desktopMode) {
                buildDesktopUserAgent(defaultUserAgent)
            } else {
                defaultUserAgent
            }
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        if (config.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startBlobDownload(blobUrl: String, contentDisposition: String?, mimeType: String?) {
        val suggestedFileName = URLUtil.guessFileName(blobUrl, contentDisposition, mimeType ?: "application/octet-stream")
        val javascript = """
            (function() {
                const blobUrl = ${org.json.JSONObject.quote(blobUrl)};
                const fileName = ${org.json.JSONObject.quote(suggestedFileName)};
                const providedMime = ${org.json.JSONObject.quote(mimeType ?: "application/octet-stream")};
                fetch(blobUrl)
                    .then(function(response) { return response.blob(); })
                    .then(function(blob) {
                        const reader = new FileReader();
                        reader.onloadend = function() {
                            const result = reader.result || '';
                            const base64 = result.indexOf(',') >= 0 ? result.split(',')[1] : result;
                            window.JustWebBlobDownloader.saveBase64File(base64, blob.type || providedMime, fileName);
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(function(error) {
                        window.JustWebBlobDownloader.reportError(String(error));
                    });
            })();
        """.trimIndent()
        webView.evaluateJavascript(javascript, null)
    }

    private fun launchBlobSavePicker(fileName: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        blobDownloadLauncher.launch(intent)
    }

    private inner class BlobDownloadBridge {
        @JavascriptInterface
        fun saveBase64File(base64: String, mimeType: String?, fileName: String?) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                    val resolvedFileName = fileName?.takeIf { it.isNotBlank() } ?: "download"
                    pendingBlobDownload = PendingBlobDownload(bytes, resolvedMime, resolvedFileName)
                    launchBlobSavePicker(resolvedFileName, resolvedMime)
                } catch (_: Exception) {
                    Toast.makeText(this@WebViewActivity, "解析下载文件失败", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun reportError(message: String?) {
            runOnUiThread {
                Toast.makeText(
                    this@WebViewActivity,
                    message?.takeIf { it.isNotBlank() } ?: "浏览器内存下载失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildDesktopUserAgent(userAgent: String): String {
        return if (userAgent.contains("X11; Linux x86_64") && !userAgent.contains(" Mobile")) {
            userAgent
        } else {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
    }

    private fun applyWindowMode() {
        val config = app ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val layoutMode = if (config.fullscreen) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = layoutMode
            }
        }

        when {
            config.fullscreen -> {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }

            else -> {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.statusBarColor = Color.BLACK
                window.navigationBarColor = Color.BLACK
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun openExternalUri(uri: Uri): Boolean {
        return try {
            val intent = buildExternalIntent(uri)
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            val fallbackUrl = buildIntentFallbackUrl(uri)
            if (!fallbackUrl.isNullOrBlank()) {
                webView.loadUrl(fallbackUrl)
            } else {
                Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun buildExternalIntent(uri: Uri): Intent {
        val intent = if (uri.scheme.equals("intent", ignoreCase = true)) {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }

        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        return intent
    }

    private fun buildIntentFallbackUrl(uri: Uri): String? {
        if (!uri.scheme.equals("intent", ignoreCase = true)) {
            return null
        }

        return try {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                .getStringExtra("browser_fallback_url")
        } catch (_: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applyWindowMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAppFromIntent(intent, savedInstanceState = null, reloadIfSameApp = false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowMode()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (this::webView.isInitialized) {
            webView.requestLayout()
        }
        applyWindowMode()
    }

    override fun onPause() {
        if (this::webView.isInitialized) {
            webView.onPause()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::webView.isInitialized) {
            val webViewState = Bundle()
            webView.saveState(webViewState)
            outState.putBundle(KEY_WEBVIEW_STATE, webViewState)
        }
    }

    override fun onDestroy() {
        when (val action = pendingPermissionAction) {
            is PendingPermissionAction.Geolocation -> action.callback.invoke(action.origin, false, false)
            is PendingPermissionAction.WebResources -> action.request.deny()
            null -> Unit
        }
        pendingPermissionAction = null
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        destroyPopupWebView()
        destroyCurrentWebView()
        super.onDestroy()
    }

    private fun destroyCurrentWebView() {
        if (this::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webViewContainer.removeView(webView)
            webView.destroy()
        }
    }
}

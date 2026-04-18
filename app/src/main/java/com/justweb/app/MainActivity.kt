package com.justweb.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isFullscreen = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
                allowContentAccess = true
                allowFileAccess = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "无法打开: $url", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    supportActionBar?.hide()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    supportActionBar?.hide()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                    super.onShowCustomView(view, callback)
                    if (view is FrameLayout) {
                        setFullscreen(true)
                    }
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    setFullscreen(false)
                }
            }
        }

        setContentView(webView)

        val url = intent?.data?.toString() ?: intent.getStringExtra("url")
        if (url.isNullOrEmpty()) {
            showHome()
        } else {
            webView.loadUrl(url)
        }

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun showHome() {
        webView.loadDataWithBaseURL(
            null,
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        color: #fff;
                        padding: 20px;
                    }
                    h1 { font-size: 2.5em; margin-bottom: 0.5em; }
                    p { color: #888; margin-bottom: 2em; font-size: 1.1em; }
                    .form {
                        width: 100%;
                        max-width: 400px;
                        background: rgba(255,255,255,0.05);
                        border-radius: 16px;
                        padding: 24px;
                        backdrop-filter: blur(10px);
                    }
                    input {
                        width: 100%;
                        padding: 16px;
                        border: none;
                        border-radius: 12px;
                        font-size: 16px;
                        background: rgba(255,255,255,0.1);
                        color: #fff;
                        margin-bottom: 16px;
                    }
                    input::placeholder { color: #666; }
                    button {
                        width: 100%;
                        padding: 16px;
                        border: none;
                        border-radius: 12px;
                        font-size: 16px;
                        font-weight: 600;
                        background: linear-gradient(90deg, #e94560, #0f3460);
                        color: #fff;
                        cursor: pointer;
                    }
                    .recent {
                        margin-top: 24px;
                        width: 100%;
                        max-width: 400px;
                    }
                    .recent h3 { color: #666; font-size: 0.9em; margin-bottom: 12px; }
                    .recent-item {
                        display: flex;
                        align-items: center;
                        padding: 12px 16px;
                        background: rgba(255,255,255,0.05);
                        border-radius: 12px;
                        margin-bottom: 8px;
                        cursor: pointer;
                    }
                    .recent-item:hover { background: rgba(255,255,255,0.1); }
                </style>
            </head>
            <body>
                <h1>JustWeb</h1>
                <p>网页转应用，一键添加到桌面</p>
                <div class="form">
                    <input type="url" id="url" placeholder="输入网址，如 https://example.com">
                    <button onclick="go()">添加到桌面</button>
                </div>
                <div class="recent" id="recent"></div>
                <script>
                    function go() {
                        var url = document.getElementById('url').value.trim();
                        if (!url) return;
                        if (!url.startsWith('http://') && !url.startsWith('https://')) {
                            url = 'https://' + url;
                        }
                        try {
                            var hostname = new URL(url).hostname;
                            var title = hostname;
                            var rec = JSON.parse(localStorage.getItem('justweb_recent') || '[]');
                            var newRec = [{title: title, url: url, time: Date.now()}, ...rec.filter(function(r) { return r.url !== url; })].slice(0, 10);
                            localStorage.setItem('justweb_recent', JSON.stringify(newRec));
                            
                            // Use Android intent
                            var intent = new WebKitIntent('android.intent.action.MAIN');
                        } catch(e) {
                            alert('请输入有效网址');
                        }
                        alert('请在浏览器中打开此页面后，添加到主屏幕');
                    }
                    
                    var recent = JSON.parse(localStorage.getItem('justweb_recent') || '[]');
                    if (recent.length) {
                        document.getElementById('recent').innerHTML = '<h3>最近打开</h3>' +
                            recent.map(function(r) { 
                                return '<div class="recent-item" onclick="location.href=\''+r.url+'\'">'+r.title+'</div>'; 
                            }).join('');
                    }
                </script>
            </body>
            </html>
            """,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        if (enabled) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.hide()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
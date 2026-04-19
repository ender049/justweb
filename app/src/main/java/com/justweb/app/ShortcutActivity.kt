package com.justweb.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.getStringExtra("url")
        val title = intent?.getStringExtra("title")

        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "无效的网址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        createShortcutNewApi(url, title ?: "JustWeb App")
        Toast.makeText(this, "已添加到主屏幕", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun createShortcutNewApi(url: String, title: String) {
        android.util.Log.d("JustWeb", "ShortcutActivity createShortcut: $url, $title")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (shortcutManager == null) {
                android.util.Log.e("JustWeb", "ShortcutManager is null")
                Toast.makeText(this, "系统不支持", Toast.LENGTH_SHORT).show()
                createShortcutLegacy(url, title)
                return
            }

            android.util.Log.d("JustWeb", "isRequestPinShortcutSupported: ${shortcutManager.isRequestPinShortcutSupported}")
            if (!shortcutManager.isRequestPinShortcutSupported) {
                Toast.makeText(this, "桌面不支持", Toast.LENGTH_SHORT).show()
                createShortcutLegacy(url, title)
                return
            }

            val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val shortcut = ShortcutInfo.Builder(this, "justweb_" + url.hashCode())
                .setShortLabel(title)
                .setLongLabel(title)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(generateIcon(title)))
                .setIntent(shortcutIntent)
                .build()

            try {
                val result = shortcutManager.requestPinShortcut(shortcut, null)
                android.util.Log.d("JustWeb", "requestPinShortcut result: $result")
                if (result) {
                    Toast.makeText(this, "已添加到主屏幕", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "桌面拒绝请求", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("JustWeb", "Shortcut error", e)
                Toast.makeText(this, "错误: " + e.message, Toast.LENGTH_SHORT).show()
            }
        } else {
            createShortcutLegacy(url, title)
        }
    }

    @Suppress("DEPRECATION")
    private fun createShortcutLegacy(url: String, title: String) {
        val shortcutIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("url", url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val addShortcutIntent = Intent().apply {
            action = "com.android.launcher.action.INSTALL_SHORTCUT"
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
            putExtra(Intent.EXTRA_SHORTCUT_ICON, generateIcon(title))
        }

        sendBroadcast(addShortcutIntent)
    }

    private fun generateIcon(text: String): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#e94560"))

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = size / 3f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val firstChar = text.first().uppercaseChar().toString()
        canvas.drawText(firstChar, size / 2f, size / 2f + paint.textSize / 3f, paint)

        return bitmap
    }
}
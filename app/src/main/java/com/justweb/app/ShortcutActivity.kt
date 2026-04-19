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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val shortcut = ShortcutInfo.Builder(this, url)
                .setShortLabel(title)
                .setLongLabel(title)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(generateIcon(title)))
                .setIntent(shortcutIntent)
                .build()

            try {
                shortcutManager.requestPinShortcut(shortcut, null)
            } catch (e: Exception) {
                Toast.makeText(this, "无法创建快捷方式", Toast.LENGTH_SHORT).show()
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
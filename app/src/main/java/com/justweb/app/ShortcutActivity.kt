package com.justweb.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.getStringExtra("url") ?: run {
            finish()
            return
        }
        val title = intent?.getStringExtra("title") ?: "App"

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#1a1a2e"))

            addView(TextView(this@ShortcutActivity).apply {
                text = "创建快捷方式"
                textSize = 24f
                setTextColor(Color.WHITE)
            })

            addView(TextView(this@ShortcutActivity).apply {
                text = url
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, 16, 0, 48)
            })

            addView(TextView(this@ShortcutActivity).apply {
                text = "添加到主屏幕"
                setTextColor(Color.WHITE)
                textSize = 18f
                setBackgroundColor(Color.parseColor("#e94560"))
                setPadding(48, 24, 48, 24)
                setCompoundDrawables(null, null, null, null)
                setOnClickListener {
                    createShortcut(url, title)
                }
            })
        })
    }

    private fun createShortcut(url: String, title: String) {
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
        finish()
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
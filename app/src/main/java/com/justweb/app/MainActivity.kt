package com.justweb.app

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var storage: WebAppStorage
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private var apps: MutableList<WebApp> = mutableListOf()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = WebAppStorage(this)
        listView = findViewById(R.id.listView)
        emptyView = findViewById(R.id.emptyView)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        adapter = AppAdapter()
        listView.adapter = adapter

        btnAdd.setOnClickListener {
            showAddEditDialog(null)
        }

        listView.setOnItemClickListener { parent, view, position, id ->
            val app = apps[position]
            openWebView(app)
        }

        listView.setOnItemLongClickListener { parent, view, position, id ->
            val app = apps[position]
            showAppOptionsDialog(app)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        apps = storage.getAll().toMutableList()
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openWebView(app: WebApp) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("app_id", app.id)
        startActivity(intent)
    }

    private fun showAddEditDialog(app: WebApp?) {
        val isEdit = app != null
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val editName = EditText(this).apply {
            hint = "应用名称"
            setText(app?.name ?: "")
        }
        val editUrl = EditText(this).apply {
            hint = "网站地址"
            setText(app?.url ?: "")
        }
        val checkNotify = CheckBox(this).apply {
            text = "允许发送通知"
            isChecked = app?.notificationsEnabled ?: false
        }

        layout.addView(TextView(this).apply { text = "应用名称" })
        layout.addView(editName)
        layout.addView(TextView(this).apply { text = "网站地址" })
        layout.addView(editUrl)
        layout.addView(checkNotify)

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "编辑应用" else "添加应用")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = editName.text.toString().trim()
                var url = editUrl.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "请输入应用名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (url.isEmpty()) {
                    Toast.makeText(this, "请输入网站地址", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }

                val newApp = WebApp(
                    id = app?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    notificationsEnabled = checkNotify.isChecked
                )
                storage.save(newApp)
                refreshList()
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAppOptionsDialog(app: WebApp) {
        val options = arrayOf("编辑", "添加到桌面", "删除")
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddEditDialog(app)
                    1 -> addToDesktop(app)
                    2 -> confirmDelete(app)
                }
            }
            .show()
    }

    private fun addToDesktop(app: WebApp) {
        try {
            var success = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = getSystemService(ShortcutManager::class.java)
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                    val intent = Intent(this, WebViewActivity::class.java).apply {
                        putExtra("app_id", app.id)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val shortcut = ShortcutInfo.Builder(this, "justweb_${app.id}")
                        .setShortLabel(app.name)
                        .setLongLabel(app.name)
                        .setIcon(Icon.createWithBitmap(generateIcon(app.name)))
                        .setIntent(intent)
                        .build()

                    success = shortcutManager.requestPinShortcut(shortcut, null)
                }
            }

            if (success) {
                Toast.makeText(this, "已添加到桌面: ${app.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "桌面不支持，使用备用方式", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("JustWeb", "addToDesktop error", e)
            Toast.makeText(this, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateIcon(text: String): android.graphics.Bitmap {
        val size = 192
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#e94560"))

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = size / 3f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val firstChar = text.first().uppercaseChar().toString()
        canvas.drawText(firstChar, size / 2f, size / 2f + paint.textSize / 3f, paint)
        return bitmap
    }

    private fun confirmDelete(app: WebApp) {
        AlertDialog.Builder(this)
            .setTitle("删除应用")
            .setMessage("确定删除 ${app.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                storage.delete(app.id)
                refreshList()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val text2 = view.findViewById<TextView>(android.R.id.text2)
            val app = apps[position]
            text1.text = app.name
            text2.text = app.url
            text2.textSize = 12f
            return view
        }
    }
}
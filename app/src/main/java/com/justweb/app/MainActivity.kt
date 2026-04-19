package com.justweb.app

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewDatabase
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.lang.Math.floorMod
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val shortcutIconColors = intArrayOf(
        android.graphics.Color.parseColor("#2563EB"),
        android.graphics.Color.parseColor("#0F766E"),
        android.graphics.Color.parseColor("#7C3AED"),
        android.graphics.Color.parseColor("#EA580C"),
        android.graphics.Color.parseColor("#DC2626"),
        android.graphics.Color.parseColor("#4F46E5")
    )

    private lateinit var storage: WebAppStorage
    private lateinit var iconStore: SiteIconStore
    private lateinit var siteDataCleaner: SiteDataCleaner
    private lateinit var listView: ListView
    private lateinit var emptyView: View
    private var apps: MutableList<WebApp> = mutableListOf()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = WebAppStorage(this)
        iconStore = SiteIconStore(this)
        siteDataCleaner = SiteDataCleaner()
        listView = findViewById(R.id.listView)
        emptyView = findViewById(R.id.emptyView)
        val headerContainer = findViewById<View>(R.id.headerContainer)
        val btnAdd = findViewById<MaterialButton>(R.id.btnAdd)
        val btnEmptyAdd = findViewById<MaterialButton>(R.id.btnEmptyAdd)

        ViewCompat.setOnApplyWindowInsetsListener(headerContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 20.dp,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        adapter = AppAdapter()
        listView.adapter = adapter
        listView.emptyView = emptyView

        btnAdd.setOnClickListener { openEditor(null) }
        btnEmptyAdd.setOnClickListener { openEditor(null) }

        listView.setOnItemClickListener { _, _, position, _ ->
            openWebView(apps[position])
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        apps = storage.getAll().sortedBy { it.name.lowercase() }.toMutableList()
        adapter.notifyDataSetChanged()
    }

    private fun openEditor(app: WebApp?) {
        val intent = Intent(this, EditWebAppActivity::class.java).apply {
            putExtras(EditWebAppActivity.createExtras(app?.id))
        }
        startActivity(intent)
    }

    private fun openWebView(app: WebApp) {
        startActivity(createWebAppIntent(app.id, fromShortcut = true))
    }

    private fun createWebAppIntent(
        appId: String,
        fromShortcut: Boolean,
        forceReload: Boolean = false,
        reuseExisting: Boolean = fromShortcut
    ): Intent {
        return Intent(this, WebViewActivity::class.java).apply {
            action = "com.justweb.app.OPEN_WEB_APP"
            putExtra("app_id", appId)
            putExtra("force_reload", forceReload)
            if (reuseExisting) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (fromShortcut) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun editApp(app: WebApp) {
        openEditor(app)
    }

    private fun showAppActions(app: WebApp) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_actions, null)
        dialogView.findViewById<TextView>(R.id.textActionTitle).text = app.name
        dialogView.findViewById<TextView>(R.id.textActionSubtitle).text = app.url
        applySiteIcon(
            dialogView.findViewById(R.id.imageActionIcon),
            dialogView.findViewById(R.id.textActionInitial),
            app,
            onLoaded = { imageView, fallbackView ->
                imageView.setImageBitmap(iconStore.load(app.id))
                imageView.visibility = View.VISIBLE
                fallbackView.visibility = View.GONE
            }
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.actionAddDesktop).setOnClickListener {
            dialog.dismiss()
            addToDesktop(app)
        }
        dialogView.findViewById<View>(R.id.actionReloadHome).setOnClickListener {
            dialog.dismiss()
            resetToHome(app)
        }
        dialogView.findViewById<View>(R.id.actionClearLogin).setOnClickListener {
            dialog.dismiss()
            confirmClearSiteData(app)
        }
        dialogView.findViewById<View>(R.id.actionClearCache).setOnClickListener {
            dialog.dismiss()
            confirmClearWebCache(app)
        }
        dialogView.findViewById<View>(R.id.actionDelete).setOnClickListener {
            dialog.dismiss()
            confirmDelete(app)
        }

        dialog.show()
    }

    private fun resetToHome(app: WebApp, showToast: Boolean = true) {
        startActivity(
            createWebAppIntent(
                appId = app.id,
                fromShortcut = true,
                forceReload = true,
                reuseExisting = true
            )
        )
        if (showToast) {
            Toast.makeText(this, "已重新加载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearSiteData(app: WebApp) {
        AlertDialog.Builder(this)
            .setTitle("清除登录")
            .setMessage("将退出 ${app.name} 的登录状态。")
            .setPositiveButton("清除") { _, _ ->
                clearSiteData(app)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearSiteData(app: WebApp) {
        if (runCatching { URL(app.url) }.getOrNull() == null) {
            Toast.makeText(this, "网站地址无效", Toast.LENGTH_SHORT).show()
            return
        }

        clearHttpAuthCacheFor(app.url)

        siteDataCleaner.clearUsing(app, SiteCleanupMode.LOGIN_STATE, null) { success ->
            if (success) {
                resetToHome(app, showToast = false)
                Toast.makeText(this, "已清除登录", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "清除失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmClearWebCache(app: WebApp) {
        AlertDialog.Builder(this)
            .setTitle("重置数据")
            .setMessage("将清除 ${app.name} 的缓存和登录状态。")
            .setPositiveButton("重置") { _, _ ->
                clearWebCache(app)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearWebCache(app: WebApp?) {
        if (app == null) {
            Toast.makeText(this, "未找到网站", Toast.LENGTH_SHORT).show()
            return
        }

        siteDataCleaner.clearUsing(app, SiteCleanupMode.WEB_CACHE, null) { success ->
            if (success) {
                resetToHome(app, showToast = false)
                Toast.makeText(this, "已重置数据", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "重置失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearHttpAuthCacheFor(url: String) {
        val host = runCatching { URL(url).host }.getOrNull().orEmpty()
        try {
            WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
        } catch (e: Exception) {
            android.util.Log.w("JustWeb", "clearHttpAuthCache failed for $host", e)
        }
    }

    private fun addToDesktop(app: WebApp) {
        try {
            var success = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = getSystemService(ShortcutManager::class.java)
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                    val shortcutId = "justweb_${app.id}"
                    val shortcut = buildPinnedShortcut(app)

                    if (shortcutManager.pinnedShortcuts.any { it.id == shortcutId }) {
                        shortcutManager.updateShortcuts(listOf(shortcut))
                        Toast.makeText(this, "桌面图标已存在，已更新", Toast.LENGTH_SHORT).show()
                        return
                    }

                    try {
                        success = shortcutManager.requestPinShortcut(shortcut, null)
                        if (success) {
                            Toast.makeText(this, "已请求添加到桌面", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("JustWeb", "requestPinShortcut failed", e)
                    }
                }
            }

            if (!success) {
                addToDesktopLegacy(app)
            }
        } catch (e: Exception) {
            android.util.Log.e("JustWeb", "addToDesktop error", e)
            Toast.makeText(this, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
            addToDesktopLegacy(app)
        }
    }

    private fun buildPinnedShortcut(app: WebApp): ShortcutInfo {
        val intent = createWebAppIntent(app.id, fromShortcut = true)
        val iconBitmap = iconStore.load(app.id) ?: generateIcon(app.id, app.name, true)
        return ShortcutInfo.Builder(this, "justweb_${app.id}")
            .setShortLabel(app.name)
            .setLongLabel(app.name)
            .setIcon(Icon.createWithAdaptiveBitmap(iconBitmap))
            .setIntent(intent)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun addToDesktopLegacy(app: WebApp) {
        try {
            val intent = createWebAppIntent(app.id, fromShortcut = true)

            val addShortcutIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, app.name)
                putExtra(Intent.EXTRA_SHORTCUT_ICON, iconStore.load(app.id) ?: generateIcon(app.id, app.name, false))
                putExtra("duplicate", false)
            }

            sendBroadcast(addShortcutIntent)
            Toast.makeText(this, "已添加到桌面", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("JustWeb", "Legacy failed", e)
            Toast.makeText(this, "添加到桌面失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeDesktopShortcut(app: WebApp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            val shortcutId = "justweb_${app.id}"
            shortcutManager?.disableShortcuts(listOf(shortcutId), "该网站工具已删除")
        }

        removeDesktopShortcutLegacy(app)
    }

    @Suppress("DEPRECATION")
    private fun removeDesktopShortcutLegacy(app: WebApp) {
        try {
            val intent = createWebAppIntent(app.id, fromShortcut = true)
            val uninstallShortcutIntent = Intent("com.android.launcher.action.UNINSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, app.name)
                putExtra("duplicate", false)
            }
            sendBroadcast(uninstallShortcutIntent)
        } catch (e: Exception) {
            android.util.Log.e("JustWeb", "removeDesktopShortcut failed", e)
        }
    }

    private fun generateIcon(seed: String, label: String, adaptive: Boolean): android.graphics.Bitmap {
        val size = 192
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val backgroundPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = shortcutIconColors[floorMod(seed.hashCode(), shortcutIconColors.size)]
        }

        val inset = if (adaptive) size * 0.18f else 0f
        val radius = if (adaptive) size * 0.24f else size * 0.18f
        val rect = android.graphics.RectF(inset, inset, size - inset, size - inset)
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            textSize = size / 2.9f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val firstChar = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
        val textY = size / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        canvas.drawText(firstChar, size / 2f, textY, paint)
        return bitmap
    }

    private fun confirmDelete(app: WebApp) {
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("删除 ${app.name}？")
            .setPositiveButton("删除") { _, _ ->
                removeDesktopShortcut(app)
                iconStore.delete(app.id)
                storage.delete(app.id)
                refreshList()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun applySiteIcon(
        imageView: ImageView,
        fallbackText: TextView,
        app: WebApp,
        onLoaded: ((ImageView, TextView) -> Unit)? = null
    ) {
        imageView.tag = app.id
        fallbackText.tag = app.id
        val cachedIcon = iconStore.load(app.id)
        if (cachedIcon != null) {
            imageView.setImageBitmap(cachedIcon)
            imageView.visibility = View.VISIBLE
            fallbackText.visibility = View.GONE
            return
        }

        imageView.visibility = View.GONE
        fallbackText.visibility = View.VISIBLE
        fallbackText.text = app.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"

        iconStore.fetchAndStore(app) { success ->
            if (success) {
                onLoaded?.invoke(imageView, fallbackText)
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
        val app = apps[position]

            applySiteIcon(
                view.findViewById(R.id.imageSiteIcon),
                view.findViewById(R.id.textInitial),
                app,
                onLoaded = { imageView, fallbackView ->
                    if (imageView.tag == app.id && fallbackView.tag == app.id) {
                        imageView.setImageBitmap(iconStore.load(app.id))
                        imageView.visibility = View.VISIBLE
                        fallbackView.visibility = View.GONE
                    }
                }
            )
            view.findViewById<TextView>(R.id.textName).text = app.name
            view.findViewById<TextView>(R.id.textUrl).text = app.url

            view.findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { editApp(app) }
            view.findViewById<MaterialButton>(R.id.btnDesktop).setOnClickListener { showAppActions(app) }

            return view
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

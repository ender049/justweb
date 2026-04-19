package com.justweb.app

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.UUID

class EditWebAppActivity : AppCompatActivity() {

    private lateinit var storage: WebAppStorage
    private lateinit var iconStore: SiteIconStore
    private var existingApp: WebApp? = null
    private var previewIconBitmap: android.graphics.Bitmap? = null
    private var previewIconUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_web_app)

        storage = WebAppStorage(this)
        iconStore = SiteIconStore(this)
        existingApp = intent.getStringExtra(EXTRA_APP_ID)?.let(storage::getById)

        val root = findViewById<android.view.View>(R.id.editRoot)
        val titleView = findViewById<TextView>(R.id.textTitle)
        val subtitleView = findViewById<TextView>(R.id.textSubtitle)
        val iconPreview = findViewById<ImageView>(R.id.imageSiteIconPreview)
        val iconFallback = findViewById<TextView>(R.id.textIconFallback)
        val iconHint = findViewById<TextView>(R.id.textIconHint)
        val btnRefreshIcon = findViewById<MaterialButton>(R.id.btnRefreshIcon)
        val nameLayout = findViewById<TextInputLayout>(R.id.layoutName)
        val urlLayout = findViewById<TextInputLayout>(R.id.layoutUrl)
        val editName = findViewById<TextInputEditText>(R.id.editName)
        val editUrl = findViewById<TextInputEditText>(R.id.editUrl)
        val switchFullscreen = findViewById<SwitchMaterial>(R.id.switchFullscreen)
        val switchDesktopMode = findViewById<SwitchMaterial>(R.id.switchDesktopMode)
        val switchKeepScreenOn = findViewById<SwitchMaterial>(R.id.switchKeepScreenOn)
        val linkPolicyInput = findViewById<AutoCompleteTextView>(R.id.inputLinkPolicy)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 12.dp,
                view.paddingRight,
                systemBars.bottom + 12.dp
            )
            insets
        }

        val isEdit = existingApp != null
        titleView.text = if (isEdit) "编辑网站" else "添加网站"
        subtitleView.text = ""

        val linkPolicyLabels = ExternalLinkPolicy.options.map { it.label }
        linkPolicyInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, linkPolicyLabels)
        )

        existingApp?.let { app ->
            editName.setText(app.name)
            editUrl.setText(app.url)
            switchFullscreen.isChecked = app.fullscreen
            switchDesktopMode.isChecked = app.desktopMode
            switchKeepScreenOn.isChecked = app.keepScreenOn
            linkPolicyInput.setText(ExternalLinkPolicy.optionFor(app.externalLinkPolicy).label, false)
            bindPreview(iconPreview, iconFallback, iconHint, app.name, iconStore.load(app.id))
        } ?: run {
            switchFullscreen.isChecked = true
            switchDesktopMode.isChecked = false
            switchKeepScreenOn.isChecked = false
            linkPolicyInput.setText(ExternalLinkPolicy.options.first().label, false)
            bindPreview(iconPreview, iconFallback, iconHint, editName.text?.toString().orEmpty(), null)
        }

        editName.doAfterTextChanged {
            if (iconPreview.drawable == null) {
                bindPreview(iconPreview, iconFallback, iconHint, it?.toString().orEmpty(), null)
            }
        }

        editUrl.doAfterTextChanged {
            previewIconUrl = null
        }

        btnRefreshIcon.setOnClickListener {
            val urlInput = editUrl.text?.toString()?.trim().orEmpty()
            val validation = UrlValidator.validate(urlInput)
            if (!validation.isValid) {
                urlLayout.error = validation.error ?: "无效的网址"
                return@setOnClickListener
            }

            urlLayout.error = null
            iconHint.text = "正在获取..."
            refreshIcon(existingApp?.id, validation.normalizedUrl) { bitmap ->
                if (bitmap != null) {
                    previewIconBitmap = bitmap
                    previewIconUrl = validation.normalizedUrl
                    iconPreview.setImageBitmap(bitmap)
                    iconPreview.visibility = android.view.View.VISIBLE
                    iconFallback.visibility = android.view.View.GONE
                    iconHint.text = "已获取图标。"
                } else {
                    bindPreview(iconPreview, iconFallback, iconHint, editName.text?.toString().orEmpty(), null)
                    iconHint.text = "未获取到图标。"
                }
            }
        }

        btnCancel.setOnClickListener { finish() }
        btnSave.setOnClickListener {
            nameLayout.error = null
            urlLayout.error = null

            val name = editName.text?.toString()?.trim().orEmpty()
            val urlInput = editUrl.text?.toString()?.trim().orEmpty()
            val validation = UrlValidator.validate(urlInput)
            var hasError = false

            if (name.isEmpty()) {
                nameLayout.error = "请输入名称"
                hasError = true
            }

            if (!validation.isValid) {
                urlLayout.error = validation.error ?: "无效的网址"
                hasError = true
            }

            if (hasError) {
                return@setOnClickListener
            }

            val updatedApp = WebApp(
                id = existingApp?.id ?: UUID.randomUUID().toString(),
                name = name,
                url = validation.normalizedUrl,
                fullscreen = switchFullscreen.isChecked,
                keepScreenOn = switchKeepScreenOn.isChecked,
                desktopMode = switchDesktopMode.isChecked,
                externalLinkPolicy = ExternalLinkPolicy.valueForLabel(linkPolicyInput.text?.toString().orEmpty())
            )
            storage.save(updatedApp)
            refreshAndStoreIcon(updatedApp)
            Toast.makeText(this, if (isEdit) "已保存" else "已添加", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun refreshIcon(appId: String?, siteUrl: String, onResult: (android.graphics.Bitmap?) -> Unit) {
        iconStore.fetchPreview(siteUrl, appId, onResult)
    }

    private fun refreshAndStoreIcon(app: WebApp) {
        val previewBitmap = previewIconBitmap
        if (previewBitmap != null && previewIconUrl == app.url) {
            iconStore.store(app.id, previewBitmap)
            return
        }

        val currentIcon = iconStore.load(app.id)
        if (currentIcon != null && existingApp?.url == app.url) {
            return
        }

        iconStore.fetchAndStore(app, forceRefresh = true)
    }

    private fun bindPreview(
        imageView: ImageView,
        fallbackView: TextView,
        hintView: TextView,
        appName: String,
        icon: android.graphics.Bitmap?
    ) {
        if (icon != null) {
            previewIconBitmap = icon
            previewIconUrl = existingApp?.url
            imageView.setImageBitmap(icon)
            imageView.visibility = android.view.View.VISIBLE
            fallbackView.visibility = android.view.View.GONE
            hintView.text = "已获取图标。"
            return
        }

        previewIconBitmap = null
        previewIconUrl = null
        imageView.setImageDrawable(null)
        imageView.visibility = android.view.View.GONE
        fallbackView.visibility = android.view.View.VISIBLE
        fallbackView.text = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
        hintView.text = "可手动刷新。"
    }

    companion object {
        private const val EXTRA_APP_ID = "app_id"

        fun createExtras(appId: String?): Bundle {
            return Bundle().apply {
                if (!appId.isNullOrBlank()) {
                    putString(EXTRA_APP_ID, appId)
                }
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

# JustWeb

一款将网站封装为 Android 全屏应用的工具应用。

## 功能特性

- **网站转应用**：输入 URL 即可创建全屏应用
- **添加到桌面**：一键创建桌面快捷方式
- **全屏浏览**：沉浸式网页浏览体验
- **应用管理**：添加、编辑、删除应用

## 应用配置

每个应用支持以下配置：

| 配置项 | 说明 | 默认值 |
|-------|------|--------|
| 应用名称 | 显示名称 | - |
| 网站地址 | URL，支持 http/https/localhost | - |
| 全屏显示 | 隐藏状态栏和导航栏 | true |
| 显示状态栏 | 显示顶部状态栏 | false |
| 允许通知 | 是否允许推送通知 | false |

## 技术栈

- **语言**：Kotlin
- **最低版本**：Android 8.0 (API 26)
- **目标版本**：Android 14 (API 34)
- **依赖**：AndroidX、Material Design、Gson

## 项目结构

```
app/src/main/
├── java/com/justweb/app/
│   ├── MainActivity.kt       # 应用列表页面
│   ├── WebViewActivity.kt   # WebView 全屏页面
│   ├── WebApp.kt          # 数据模型 + URL 验证
│   └── WebAppStorage.kt   # 数据持久化
└── res/
    └── layout/
        ├── activity_main.xml  # 主页面布局
        └── item_app.xml      # 应用列表项布局
```

## 构建

```bash
# 本地构建（需要 Android SDK）
cd android
./gradlew assembleDebug

# 或使用 GitHub Actions（自动构建）
git push
```

构建产物位于：`app/build/outputs/apk/debug/`

## 开发指南

### 添加新应用

```kotlin
val app = WebApp(
    name = "应用名称",
    url = "https://example.com",
    fullscreen = true,
    showStatusBar = false,
    notificationsEnabled = false
)
storage.save(app)
```

### 启动 WebView

```kotlin
val intent = Intent(context, WebViewActivity::class.java)
intent.putExtra("app_id", appId)
startActivity(intent)
```

### 验证 URL

```kotlin
val result = UrlValidator.validate(inputUrl)
if (result.isValid) {
    // 使用 result.normalizedUrl
} else {
    // 显示错误 result.error
}
```

## 相关资源

- [Android 快捷方式文档](https://developer.android.com/develop/ui/launch-shortcuts)
- [WebView 开发指南](https://developer.android.com/guide/webapps/)
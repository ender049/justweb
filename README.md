# JustWeb

JustWeb is a lightweight Android app that turns websites into simple full-screen tools.

It is designed for people who want to save a few frequently used sites, open them quickly, and use them like standalone apps without building a full browser UI.

## Features

- Save websites and open them from the list or launcher shortcuts
- Full-screen website runtime with minimal chrome
- Per-site options for fullscreen, desktop mode, keep screen on, and external link behavior
- Site icon fetching from PWA `manifest/icons` with fallback handling
- File upload, file download, and `blob:` download support
- Popup and OAuth-friendly WebView behavior
- Per-site data reset for login and cache cleanup
- Support for `http://`, LAN IPs, localhost, and mixed-content sites

## Product Scope

JustWeb is intentionally not a browser.

It does not aim to provide:

- tabs
- bookmarks or history pages
- a persistent address bar
- a persistent top toolbar
- script managers, blockers, or browser-style advanced settings

## Screens

- Main screen: manage saved websites
- Edit screen: configure one website
- Runtime screen: open the website in a dedicated WebView

## Tech Stack

- Kotlin
- Android WebView
- AndroidX WebKit
- Material Components
- Gson
- Jsoup
- AndroidSVG

## Requirements

- Min SDK: 26
- Target SDK: 34
- JDK: 17

## Project Structure

```text
app/src/main/
├── java/com/justweb/app/
│   ├── MainActivity.kt
│   ├── EditWebAppActivity.kt
│   ├── WebViewActivity.kt
│   ├── WebApp.kt
│   ├── WebAppStorage.kt
│   ├── SiteIconStore.kt
│   ├── SiteDataCleaner.kt
│   └── WebViewProfileManager.kt
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── activity_edit_web_app.xml
    │   ├── activity_web_view.xml
    │   ├── dialog_app_actions.xml
    │   └── item_app.xml
    └── values/
        ├── colors.xml
        ├── strings.xml
        └── themes.xml
```

## Build

Debug build:

```bash
./gradlew assembleDebug
```

Release build:

```bash
./gradlew assembleRelease
```

APK output:

- `app/build/outputs/apk/debug/`
- `app/build/outputs/apk/release/`

## Signing

If you want to build a signed release APK, copy `keystore.properties.example` to `keystore.properties` and fill in your own values.

Do not commit `keystore.properties` or any real keystore files.

## Testing

Manual test checklist:

- `docs/manual-test-checklist.md`

## Notes

- The runtime page should stay minimal.
- Management and recovery actions belong on the main screen.
- New features should be judged against the core goal: using websites like simple tools, not like browser tabs.

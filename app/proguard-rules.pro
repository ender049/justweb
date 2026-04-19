# Preserve generic signatures for Gson TypeToken usage.
-keepattributes Signature

# Keep app classes while we stabilize release minification.
-keep class com.justweb.app.** { *; }

# Preserve JavascriptInterface annotations and methods used by WebView bridges.
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve Gson model field names used for persisted site configuration.
-keep class com.justweb.app.WebApp { *; }
-keep class com.justweb.app.UrlValidation { *; }
-keep class com.justweb.app.ExternalLinkPolicy { *; }
-keep class com.justweb.app.ExternalLinkPolicy$Option { *; }

# Preserve TypeToken metadata used when deserializing stored lists.
-keep class com.google.gson.reflect.TypeToken { *; }

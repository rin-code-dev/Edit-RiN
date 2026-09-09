# WebView JavaScript bridge methods are called by name from JavaScript.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Phase 4 — keep Room, AI, and model classes for R8 (isMinifyEnabled=true)
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }
-keep class com.example.ai.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod
-keepnames class com.example.BuildConfig { *; }

# Original template
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

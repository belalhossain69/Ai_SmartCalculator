# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- 1. JNI & AI Rules (Essential for Llama) ---
# This prevents the names of your C++ functions from being scrambled.
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- 2. Math Engine (Symja) Rules ---
# Symja uses complex logic that ProGuard might accidentally delete.
-keep class org.matheclipse.** { *; }
-dontwarn org.matheclipse.**

# --- 3. Logging & Bridges ---
# Fixes crashes related to the math solver's logging system.
-keep class org.apache.logging.log4j.** { *; }
-keep class org.slf4j.** { *; }
-dontwarn org.apache.logging.log4j.**

# --- 4. OCR & Image Loading ---
-keep class com.google.mlkit.** { *; }
-keep public class * extends com.bumptech.glide.module.AppGlideModule
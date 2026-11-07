# Keep the manifest Application class and its Hilt-injected startup entry point stable on older ART runtimes.
-keep class com.dsann.music.DsmusicApplication { *; }
-keep class com.dsann.music.Hilt_DsmusicApplication { *; }

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============ Glide ============
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============ MediaStore / ContentProvider ============
-keep class android.provider.MediaStore { *; }
-keep class android.provider.MediaStore$Audio { *; }
-keep class android.provider.MediaStore$Audio$Media { *; }

# ============ Custom Views ============
-keep class com.dsann.music.ui.custom.** { *; }

# ============ Palette ============
-keep class androidx.palette.graphics.** { *; }

# ============ Navigation ============
-keep class androidx.navigation.** { *; }

# ============ Lifecycle ============
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LiveData { *; }

# ============ Lyrics & JSON Models ============
# Keep lyrics data classes (Gson needs field names to match JSON)
-keep class com.dsann.music.data.lyrics.** { *; }
-keepattributes *Annotation*
-keepattributes Signature

# ============ Retrofit & OkHttp ============
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# ============ Gson ============
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ============ Jaudiotagger ============
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# ============ InnerTube & Models ============
-keep class com.dsann.music.data.innertube.** { *; }
-keep class com.dsann.music.model.** { *; }

# ============ NewPipe Extractor & Jsoup ============
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ============ FFmpegKit ============
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# ============ Rhino (org.mozilla.javascript) ============
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**

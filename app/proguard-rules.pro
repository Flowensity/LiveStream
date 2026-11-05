# ==============================================================================
# LiveStream Proguard / R8 Optimization & Obfuscation Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Kotlin & Coroutines Metadata
# ------------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**

# Keep Kotlinx Coroutines internals
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# 2. Kotlinx Serialization
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    *;
}
-keepclassmembers class * {
    *** Companion;
}
-keepnames class * implements kotlinx.serialization.KSerializer
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    public static *** INSTANCE;
}

# Keep serializable domain and data models
-keep @kotlinx.serialization.Serializable class com.livestream.core.model.** { *; }

# ------------------------------------------------------------------------------
# 3. AndroidX Room Database
# ------------------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ------------------------------------------------------------------------------
# 4. AndroidX Media3 & ExoPlayer
# ------------------------------------------------------------------------------
# Preserve Media3 common, exoplayer, HLS, DASH, and UI components
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.exoplayer.hls.** { *; }
-keep class androidx.media3.exoplayer.dash.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# Keep MediaCodec and Hardware Decoder reflection lookups
-keepclassmembers class androidx.media3.exoplayer.mediacodec.** {
    *;
}

# ------------------------------------------------------------------------------
# 5. Dagger / Hilt
# ------------------------------------------------------------------------------
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.components.**
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }

# ------------------------------------------------------------------------------
# 6. OkHttp & Retrofit
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ------------------------------------------------------------------------------
# 7. WebView & JavaScript Interface (Official Web Playback Container)
# ------------------------------------------------------------------------------
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.livestream.playback.web.** {
    *;
}

# ------------------------------------------------------------------------------
# 8. Jetpack Compose
# ------------------------------------------------------------------------------
-keepclassmembers class androidx.compose.ui.platform.WrappedComposition {
    *;
}
-keepclassmembers class * extends androidx.compose.ui.Modifier {
    *;
}

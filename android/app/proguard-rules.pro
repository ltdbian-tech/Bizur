# Bizur Android ProGuard rules

# Compose / runtime keep rules
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# Ktor + coroutines
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Serialization + Signal protocol
-keep class kotlinx.serialization.** { *; }
-keep class org.whispersystems.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-dontwarn org.whispersystems.**

# WebRTC
-dontwarn org.webrtc.**
-keep class org.webrtc.** { *; }

# Room generated models
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-keep class com.bizur.android.** extends androidx.room.RoomDatabase

# Keep WorkManager + databinding annotations
-keepattributes *Annotation*

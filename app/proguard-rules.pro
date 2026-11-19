# ZopHop ProGuard / R8 Keep Rules

# Keep Data Models and Database Entities
-keep class dev.zophop.data.** { *; }

# Keep Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep WorkManager Workers
-keep class dev.zophop.worker.** { *; }

# Keep Services, TileService and Broadcast Receivers
-keep class dev.zophop.service.** { *; }
-keep class dev.zophop.receiver.** { *; }

# OkHttp & Okio Network Client
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# EncryptedSharedPreferences / Google Tink Annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

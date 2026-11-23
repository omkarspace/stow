# ProGuard / R8 Rules for Stow

# Kotlinx Serialization Rules
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}
-keepclassmembers class com.cleansaver.app.data.remote.dto.** {
    <fields>;
    <methods>;
}
-keepclassmembers class com.cleansaver.app.domain.model.** {
    <fields>;
    <methods>;
}

# Room SQLite Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.cleansaver.app.data.local.CleanSaverDatabase_Impl {
    <init>(...);
}
-keepclassmembers class com.cleansaver.app.data.local.** {
    <fields>;
    <methods>;
}

# OkHttp & Okio Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines Rules
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WorkManager Rules
-keep class * extends androidx.work.Worker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.cleansaver.app.data.worker.MediaDownloadWorker {
    <init>(...);
}

# youtubedl-android / yt-dlp / FFmpeg / Aria2c
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**



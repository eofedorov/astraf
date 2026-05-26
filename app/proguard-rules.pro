# MapLibre: JNI nativePtr (consumer rules в AAR; дублируем на случай R8-регрессии).
-keepclasseswithmembers class org.maplibre.android.maps.renderer.** {
    long nativePtr;
}
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.**

# Компоненты из AndroidManifest.xml (activity/service/receiver).
-keep class com.astraf.hrgpslogger.MainActivity { *; }
-keep class com.astraf.hrgpslogger.LoggingForegroundService { *; }
-keep class com.astraf.hrgpslogger.BootCompletedReceiver { *; }
-keep class com.astraf.hrgpslogger.HrGpsLoggerApp { *; }

# OkHttp / Play Services — подавляем шум, keep приходит из consumer-rules AAR.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Меньше мусора в release на устройстве.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

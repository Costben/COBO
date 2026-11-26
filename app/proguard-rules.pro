# 保留行号，崩溃栈可读
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp / Okio 的反射入口
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil 3 通过 ServiceLoader 找 ImageLoader 组件
-keep class coil3.** { *; }
-keepclassmembers class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keepclassmembers class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# Media3 播放器的可选组件按需反射加载
-dontwarn androidx.media3.**

# libwbutil.so 在 JNI_OnLoad 与签名计算中按固定类名/方法签名查找 com.sina.weibo.**
-keep class com.sina.weibo.** { *; }

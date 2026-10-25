# MuTag 性能优先 R8 优化规则

# 1. 深度代码优化设置
-optimizationpasses 5
-allowaccessmodification

# 2. Kotlinx Serialization 序列化保护
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# 3. 核心业务模型保护
-keep class org.mutag.model.** { *; }
-keep class org.mutag.core.tagger.scanner.** { *; }
-keep class org.mutag.core.tagger.writer.MetadataPayload { *; }
-keep class org.mutag.app.settings.SettingsState { *; }

# 4. 网络库与图片库 (OkHttp, Okio, Coil)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn coil3.**
-dontwarn coil.**

# 5. 剥离无用调试日志以提升性能
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# 6. 保留堆栈行号便于分析
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

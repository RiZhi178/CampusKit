# release 开启 R8 收缩与混淆（重命名）：体积更小，且崩溃日志仍需可定位，
# 故保留源文件与行号；用 build 输出的 mapping.txt 可还原被混淆的堆栈。
# 收缩与优化后仍保留源文件与行号；mapping 只用于还原被内联的帧。
-keepattributes SourceFile,LineNumberTable

# 反射与序列化相关：kotlinx.serialization 生成的序列化器需保留。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class qdu.campus.** {
    *** Companion;
}
-keepclasseswithmembers class qdu.campus.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# security-crypto 依赖的 tink 引用了编译期注解，运行时不需要
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

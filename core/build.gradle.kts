plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// 项目在含中文的路径下时，Windows 的 java.exe 启动器会按本机代码页(GBK)解码
// Gradle 以 UTF-8 写入的 @argfile，导致 build/classes 这些非 ASCII 路径条目被解坏，
// 分叉的测试 JVM 因此对全部测试类抛 ClassNotFoundException。
// 解法：把本模块构建产物重定向到纯 ASCII 目录，让整条测试 classpath 只含 ASCII 路径。
// 仅在项目路径含非 ASCII 字符时启用；ASCII 路径的机器仍用默认 core/build。
val projectPathIsAscii = file(".").canonicalPath.all { it.code < 128 }
if (!projectPathIsAscii) {
    layout.buildDirectory.set(
        file(System.getProperty("user.home") + "/.qdu-campus/core-build")
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // NoticeParser 依赖 Jsoup 解析服务端渲染 HTML。
    api(libs.jsoup)
    // 领域 DTO 的 @Serializable 由序列化插件生成，需要序列化运行时。
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

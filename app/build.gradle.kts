plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ── release 签名 ───────────────────────────────────────────────────────────
// 口令不进仓库：先读环境变量，再读用户级 ~/.gradle/gradle.properties。
// keystore 本体也在仓库外（默认 ~/.qdu-campus/keystore/release.jks）。
// ⚠️ 这个 keystore 和口令一旦丢失，已装出去的包就再也不能覆盖升级，必须自己备份。
fun signingSecret(name: String): String? =
    System.getenv(name) ?: (project.findProperty(name) as? String)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("QDU_RELEASE_STORE_FILE")
val releaseStorePassword = signingSecret("QDU_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingSecret("QDU_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingSecret("QDU_RELEASE_KEY_PASSWORD") ?: releaseStorePassword
val releaseSigningReady = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank()

if (!releaseSigningReady) {
    logger.warn(
        "⚠️ release 签名未配置：QDU_RELEASE_STORE_FILE / QDU_RELEASE_STORE_PASSWORD / " +
            "QDU_RELEASE_KEY_ALIAS 均未找到（环境变量或 ~/.gradle/gradle.properties）。" +
            "assembleRelease 会产出未签名包，装不上。",
    )
}

android {
    namespace = "qdu.campus"
    compileSdk = 37

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        // 安装包标识。与 Kotlin 的 namespace（qdu.campus）刻意分开：
        // 改 applicationId 只换「手机上是谁」，不用动源码包名与 import。
        // 换了它就是一个新应用，旧的不会被覆盖，得手动卸载。
        applicationId = "qdu.campuskit"
        minSdk = 33
        targetSdk = 37
        // 版本规则见 README：三位数字，第一位大版本（只在明确要求时改），
        // 第二位小版本（功能 / UI / 底层改动，上限 9），第三位 bug 修复计数（上限 99）。
        // versionCode = 第一位×10⁴ + 第二位×10³ + 第三位，与版本名逐位对应，任何一位增加都单调递增。
        // 0.9.x 是 1.0 正式版的预备系列：第二位已顶到上限 9，预备阶段的新功能继续走第三位，
        // 1.0.0 留给正式版本身。
        val appMajor = 0
        val appMinor = 9
        val appPatch = 15
        val appStage = "alpha"
        versionCode = appMajor * 10_000 + appMinor * 1_000 + appPatch
        versionName = "$appMajor.$appMinor.$appPatch-$appStage"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // 包名与 release 一致：两种构建装到同一台机器上是同一个 App
            // （数据、登录态、通知权限都连续，不需要为调试再登一次）。
            // 代价是二者不能同时装，切换时互相覆盖。
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // 同包名再用不同签名会出现「签名不一致，装不上」，所以 debug 也用同一把
            // release keystore；没配置签名时留空，退回 Android 默认的 debug 密钥。
            signingConfig = signingConfigs.findByName("release")
        }
        release {
            // 只收缩不重命名：体积从 51 MB 降到 4 MB 左右，
            // 崩溃日志仍保留类名、方法名与行号，导出后可直接读。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 用自己生成的 release keystore 签名，配置在仓库外（见文件顶部 signingConfigs）。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// R8 的 mapping.txt 只活在 build 目录里，一次 clean 就没了，
// 而它是还原混淆堆栈的唯一依据。归档到仓库外的固定目录，
// 与 core/build.gradle.kts 里那条 ASCII 构建路径用同一处位置。
val releaseMappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
val mappingArchiveDir = File(System.getProperty("user.home"), ".qdu-campus/release-mapping")
val releaseVersion = "${android.defaultConfig.versionName ?: "0"}(${android.defaultConfig.versionCode ?: 0})"

val archiveReleaseMapping = tasks.register("archiveReleaseMapping") {
    group = "build"
    description = "复制 release 的 R8 mapping.txt 到 ~/.qdu-campus/release-mapping，按版本号命名"
    doLast {
        // 未开代码收缩时 AGP 不注册这条 R8 任务，顺带避开 build 目录里的历史 mapping.txt
        if (!tasks.names.contains("minifyReleaseWithR8")) {
            logger.lifecycle("当前 release 未开代码收缩，无 mapping.txt 可归档")
            return@doLast
        }
        val mapping = releaseMappingFile.get().asFile
        if (!mapping.exists()) {
            logger.lifecycle("未找到 $mapping，本次构建没有产出 mapping.txt")
            return@doLast
        }
        mappingArchiveDir.mkdirs()
        val dest = File(mappingArchiveDir, "mapping-$releaseVersion.txt")
        mapping.copyTo(dest, overwrite = true)
        logger.lifecycle("mapping.txt 已归档：$dest")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(archiveReleaseMapping) }

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // extended 传递依赖 core，单列 core 是重复声明
    implementation(libs.compose.material.icons.extended)
    implementation(libs.material.color.utilities)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    implementation(libs.backdrop)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

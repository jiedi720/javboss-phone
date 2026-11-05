import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// release 签名配置：keystore.properties 不入库（见 .gitignore）
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.javboss.phone"
    compileSdk = 35

    defaultConfig {
        // 与原版（com.javboss.phone）刻意区分：换 applicationId 才能与原版共存、不覆盖安装。
        // 仅改 versionCode 做不到这一点（同包名 + 更高 versionCode 是「覆盖升级」）。
        applicationId = "com.javboss.phone.preset"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "0.3.23-modified"

        // 设置页展示的打包时间（本地时区，精确到分钟）
        // 注意：Kotlin DSL 里 `java` 会被解析成 JavaPluginExtension，必须走顶部的具名 import
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())}\"",
        )
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "keystore/release.jks"))
            storePassword = keystoreProperties.getProperty("storePassword", "")
            keyAlias = keystoreProperties.getProperty("keyAlias", "")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        // 本机调试包也复用发布签名，避免 debug/release 交替安装时被 Android 视为不同应用。
        // keystore 不入库；其他开发者未配置时仍使用默认 debug keystore。
        debug {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            // R8 裁剪：未引用的类（含 material-icons-extended 里 1 万多个图标）会被剥掉，
            // 资源压缩再顺手去掉未引用的图片/字符串。keep 规则见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // libvlc 每个 ABI 约 30MB 原生库。这里只打单一架构（arm64-v8a）：
    // 与 VLC（vlc-android-src 的 signedRelease 产物同样只含 arm64-v8a）保持一致，
    // 构建产物固定为 app-arm64-v8a-release.apk，不再输出 armeabi-v7a / x86_64 / universal。
    // 需要别的架构时，把下面 include 换成对应 ABI 重新构建即可。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 设置页要显示版本号与打包时间
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.libvlc.all)
    implementation(libs.nanohttpd)

    testImplementation(libs.junit4)
}

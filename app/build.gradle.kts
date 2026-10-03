plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 1
    } catch (_: Exception) {
        1
    }
}

val gitCount = getGitCommitCount()
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
// 基础偏移量 3100，确保高于之前编译出的旧版本号 (1~3025)，保证覆盖安装 100% 成功
val currentBuild = ciRunNumber ?: gitCount
val appVersionCode = 3100 + currentBuild
val appVersionName = "1.0.$currentBuild"

android {
    namespace = "com.antigravity.sshwake"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.antigravity.sshwake"
        minSdk = 26
        targetSdk = 34
        // 自动递增版本号，确保无论本地还是云端构建均高于已安装版本，支持系统顺畅覆盖升级
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 项目固定的开发与共享证书，确保无论在任何一台云端虚拟机还是本地构建，签名均 100% 绝对一致，杜绝“签名不一致导致无法更新”
        create("shared") {
            val keystoreFile = rootProject.file("keystore/debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val keystoreFile = file("release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("ALIAS") ?: "wakekey"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEY_STORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseKeystore = file("release.jks")
            if (releaseKeystore.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        debug {
            isMinifyEnabled = false
            // 统一签名，确保每次覆盖安装均能成功
            signingConfig = signingConfigs.getByName("shared")
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
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // Android 原生组件
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Lifecycle & Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room 持久化数据库
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // 安全加密存储（基于 Android Keystore）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SSH 客户端与加解密引擎 (SSHJ + BouncyCastle)
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // 自动化单元测试框架
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dependencycheck)
}

// Security Scan workflow 用: OWASP Dependency-Check（依存ライブラリの CVE 照合）。
// `:app:dependencyCheckAnalyze` で実行する。posture は「助言」なので failBuildOnCVSS=11
// を指定して Gradle ビルドは決して fail させず、所見は SARIF（GitHub Security タブ）と
// HTML artifact で可視化する。NVD_API_KEY（env）があれば NVD ミラー取得が高速化する。
dependencyCheck {
    formats = listOf("SARIF", "HTML")
    failBuildOnCVSS = 11.0f
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
        delay = 4000
    }
    data {
        // workflow の actions/cache パス（~/.gradle/dependency-check-data）と一致させる。
        directory = "${System.getProperty("user.home")}/.gradle/dependency-check-data"
    }
}

// Issue #9 Req 3.1 / Task 1.2 (Issue #58: applicationId / namespace 再移行済み):
// Room schema export location.
// Required by MigrationTestHelper so it can load
// app/schemas/io.github.hitoshiichikawa.keynest.data.KeyNestDatabase/<version>.json and
// verify v1 -> v2 migrations apply cleanly.
ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

// Release signing credentials.
// Values are read from ~/.gradle/gradle.properties (user-level, never committed).
// If any are missing, signing is skipped and release builds will be unsigned —
// fine for local debug-style smoke tests, but they cannot be uploaded to Play.
val keynestStoreFile = findProperty("KEYNEST_UPLOAD_STORE_FILE") as String?
val keynestStorePassword = findProperty("KEYNEST_UPLOAD_STORE_PASSWORD") as String?
val keynestKeyAlias = findProperty("KEYNEST_UPLOAD_KEY_ALIAS") as String?
val keynestKeyPassword = findProperty("KEYNEST_UPLOAD_KEY_PASSWORD") as String?
val keynestSigningReady = keynestStoreFile != null &&
    keynestStorePassword != null &&
    keynestKeyAlias != null &&
    keynestKeyPassword != null

android {
    namespace = "io.github.hitoshiichikawa.keynest"
    // Issue #145 / Req 1.2: compileSdk を Android 16 (API 36) に引き上げる。
    // targetSdk = 36 に必要な platform シンボル解決のため両方 36。
    // AGP 8.6.1 は compileSdk 36 を未サポート扱いとして警告するが、Google 公式の
    // エスケープハッチ `android.suppressUnsupportedCompileSdk=36`（gradle.properties）
    // で警告のみ抑止し、ビルド自体は成立する。AGP/Gradle/Kotlin/KSP の恒久 bump は
    // 本 Issue の Out of Scope（依存ライブラリの自発的メジャーアップグレード除外）。
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hitoshiichikawa.keynest"
        minSdk = 26
        // Issue #145 / Req 1.1: Play Console の 2026-08-31 以降の target API ポリシー
        // 準拠のため、targetSdk を Android 16 (API 36) に引き上げる。
        // minSdk = 26 は Req 2.1 により据え置き。
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keynestSigningReady) {
                storeFile = file(keynestStoreFile!!)
                storePassword = keynestStorePassword
                keyAlias = keynestKeyAlias
                keyPassword = keynestKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (keynestSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        unitTests {
            isIncludeAndroidResources = true
            // Allow unit tests to invoke android.util.Log etc. without
            // mocking each call: framework methods return their default
            // value (0 / null / false) instead of throwing.
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.credentials)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.room.testing)
}

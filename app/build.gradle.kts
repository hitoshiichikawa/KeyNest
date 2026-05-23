plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hitoshiichikawa.keynest"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

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

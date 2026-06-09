// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    // OWASP Dependency-Check は app モジュールでのみ適用する（Security Scan workflow 用）。
    alias(libs.plugins.dependencycheck) apply false
}

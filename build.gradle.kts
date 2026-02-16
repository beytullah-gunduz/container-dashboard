plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "com.dockerdashboard"
    version = "1.0.0"
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}

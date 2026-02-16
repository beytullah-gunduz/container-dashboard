import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    // Suppress Beta warning for expect/actual classes
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {

                // Android DataStore
                implementation(libs.bundles.android.datastore)

                // Compose (using explicit coordinates as compose.* aliases are deprecated in 1.10)
                implementation(libs.bundles.compose.common)

                // Material3 Adaptive (AnimatedPane, adaptive layouts)
                implementation(libs.bundles.compose.material3.adaptive)

                // Lifecycle ViewModel
                implementation(libs.bundles.lifecycle)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)

                // Serialization
                implementation(libs.kotlinx.serialization.json)

                // DateTime
                implementation(libs.kotlinx.datetime)

                // Ktor Client for Docker API
                implementation(libs.bundles.ktor.common)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)

                // Ktor Client CIO engine for desktop
                implementation(libs.ktor.client.cio)

                // Docker Java client
                implementation(libs.bundles.docker.java)

                // SLF4J + Logback logging
                implementation(libs.bundles.logging)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.containerdashboard.MainKt"

        nativeDistributions {

            modules("java.sql")
            modules("java.naming")
            modules("jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.containerdashboard"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.containerdashboard"
            }
        }
    }
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.materialIconsExtended)
                
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
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.containerdashboard.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.containerdashboard"
            packageVersion = "1.0.0"
            
            macOS {
                bundleID = "com.containerdashboard"
            }
        }
    }
}

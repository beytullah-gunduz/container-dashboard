import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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

                // JediTerm terminal emulator
                implementation(libs.jediterm.core)
                implementation(libs.jediterm.ui)

                // SLF4J + Logback logging
                implementation(libs.bundles.logging)

                // JNA for AppKit bridge (macOS native window drag, multi-monitor)
                implementation(libs.jna)
            }
        }
    }
}

val appVersion: String =
    project
        .findProperty("app.version")
        ?.toString()
        ?.removeSuffix("-SNAPSHOT") ?: "1.0.0"

// ---------------------------------------------------------------------------
// Generate a tiny BuildConfig.kt so runtime UI (Settings > About) can show
// the same version string Gradle uses for native packaging, rather than
// hardcoding "1.0.0" in a Compose file.
// ---------------------------------------------------------------------------
val generatedBuildConfigDir = layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin")

val generateBuildConfig by tasks.registering {
    val outDir = generatedBuildConfigDir
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val pkgDir =
            outDir
                .get()
                .asFile
                .resolve("com/containerdashboard")
                .apply { mkdirs() }
        pkgDir.resolve("BuildConfig.kt").writeText(
            """
            package com.containerdashboard

            /** Generated at build time from `app.version` in `gradle.properties`. */
            internal object BuildConfig {
                const val VERSION: String = "$version"
            }

            """.trimIndent(),
        )
    }
}

kotlin {
    sourceSets {
        named("commonMain") {
            kotlin.srcDir(generateBuildConfig)
        }
    }
}

// ---------------------------------------------------------------------------
// Generated macOS app icon.
// Renders the container-dashboard package box at every iconset size
// (16/32/128/256/512 plus retina @2x variants up to 1024) and pipes them
// through `iconutil -c icns` to produce a multi-resolution .icns that
// macOS will display in the Dock, Finder, Launchpad, and Cmd+Tab.
//
// The drawing code mirrors `AppIconImage.kt.renderAppIconAwt` — if one
// is updated, update the other so the runtime Taskbar icon and the
// packaged Dock icon stay visually in sync.
// ---------------------------------------------------------------------------
val generatedIconsDir = layout.buildDirectory.dir("generated-icons")
val generatedIcnsFile = generatedIconsDir.map { it.file("container-dashboard.icns") }

val generateMacIcon by tasks.registering {
    val outDir = generatedIconsDir
    outputs.dir(outDir)
    doLast {
        val iconsetDir = outDir.get().asFile.resolve("container-dashboard.iconset")
        iconsetDir.deleteRecursively()
        iconsetDir.mkdirs()

        val sizes = listOf(16, 32, 128, 256, 512)
        for (size in sizes) {
            ImageIO.write(renderContainerIcon(size), "png", iconsetDir.resolve("icon_${size}x$size.png"))
            ImageIO.write(renderContainerIcon(size * 2), "png", iconsetDir.resolve("icon_${size}x$size@2x.png"))
        }

        val icns = outDir.get().asFile.resolve("container-dashboard.icns")
        val proc =
            ProcessBuilder(
                "iconutil",
                "-c",
                "icns",
                "-o",
                icns.absolutePath,
                iconsetDir.absolutePath,
            ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw GradleException("iconutil failed (exit $exitCode): $output")
        }
    }
}

fun renderContainerIcon(size: Int): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    val padding = size * 0.1f
    val boxWidth = size - padding * 2
    val boxHeight = size - padding * 2

    val boxColor = Color(0xD4, 0xA5, 0x74)
    val boxDark = Color(0xB8, 0x95, 0x6A)
    val boxLight = Color(0xE8, 0xC9, 0xA0)
    val tapeColor = Color(0xF5, 0xE6, 0xD3)
    val strokeColor = Color(0x8B, 0x73, 0x55)

    fun poly(vararg points: Pair<Float, Float>): Polygon {
        val xs = IntArray(points.size)
        val ys = IntArray(points.size)
        points.forEachIndexed { i, (x, y) ->
            xs[i] = x.toInt()
            ys[i] = y.toInt()
        }
        return Polygon(xs, ys, points.size)
    }

    fun fillAndStroke(
        polygon: Polygon,
        fill: Color,
        strokeWidth: Float,
    ) {
        g.color = fill
        g.fill(polygon)
        g.color = strokeColor
        g.stroke = BasicStroke(strokeWidth.coerceAtLeast(1f))
        g.draw(polygon)
    }

    // Front
    fillAndStroke(
        poly(
            padding to (padding + boxHeight * 0.25f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth) to (padding + boxHeight * 0.25f),
            (padding + boxWidth) to (padding + boxHeight * 0.85f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight),
            padding to (padding + boxHeight * 0.85f),
        ),
        boxColor,
        size * 0.006f,
    )
    // Left face
    fillAndStroke(
        poly(
            padding to (padding + boxHeight * 0.25f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight),
            padding to (padding + boxHeight * 0.85f),
        ),
        boxDark,
        size * 0.004f,
    )
    // Right face
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth) to (padding + boxHeight * 0.25f),
            (padding + boxWidth) to (padding + boxHeight * 0.85f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight),
        ),
        boxLight,
        size * 0.004f,
    )
    // Top (lid)
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f) to padding,
            (padding + boxWidth) to (padding + boxHeight * 0.15f),
            (padding + boxWidth) to (padding + boxHeight * 0.25f),
            (padding + boxWidth * 0.5f) to (padding + boxHeight * 0.4f),
            padding to (padding + boxHeight * 0.25f),
            padding to (padding + boxHeight * 0.15f),
        ),
        boxLight,
        size * 0.006f,
    )
    // Top tape
    val tapeWidth = boxWidth * 0.15f
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f - tapeWidth * 0.5f) to (padding + boxHeight * 0.02f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.5f) to (padding + boxHeight * 0.02f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.5f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f - tapeWidth * 0.5f) to (padding + boxHeight * 0.4f),
        ),
        tapeColor,
        size * 0.003f,
    )
    // Front tape
    fillAndStroke(
        poly(
            (padding + boxWidth * 0.5f - tapeWidth * 0.3f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.3f) to (padding + boxHeight * 0.4f),
            (padding + boxWidth * 0.5f + tapeWidth * 0.3f) to (padding + boxHeight),
            (padding + boxWidth * 0.5f - tapeWidth * 0.3f) to (padding + boxHeight),
        ),
        tapeColor,
        size * 0.003f,
    )

    g.dispose()
    return img
}

// Ensure every packaging task regenerates the icon first. CI invocations
// of `packageReleaseDmg` / `createDistributable` / etc. pick up icon
// changes without a manual bootstrap step.
tasks
    .matching { task ->
        task.name.startsWith("package") || task.name.contains("Distributable", ignoreCase = true)
    }.configureEach {
        dependsOn(generateMacIcon)
    }

compose.desktop {
    application {
        mainClass = "com.containerdashboard.MainKt"

        nativeDistributions {

            modules("java.sql")
            modules("java.naming")
            modules("jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ContainerDashboard"
            packageVersion = appVersion

            macOS {
                bundleID = "com.containerdashboard"
                iconFile.set(generatedIcnsFile)
            }
        }

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

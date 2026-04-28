# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# kotlinx.serialization
# The @Serializable models rely on synthetic `$serializer` classes and
# `Companion.serializer()` methods that ProGuard cannot prove reachable.
# Without these rules, nested/collection fields (Ports, Labels, etc.)
# silently decode to their default empty values.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.containerdashboard.**$$serializer { *; }
-keepclassmembers class com.containerdashboard.** {
    *** Companion;
}
-keepclasseswithmembers class com.containerdashboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Apache HttpClient5 (pulled in by docker-java-transport-httpclient5)
# HTTP/2 support lives in the separate httpcore5-h2 artifact we don't ship —
# we only talk to the Docker Unix socket over HTTP/1.1.
# ---------------------------------------------------------------------------
-dontwarn org.apache.hc.core5.http2.**

# ---------------------------------------------------------------------------
# Optional TLS and compression providers referenced by Apache HC5 / Commons
# ---------------------------------------------------------------------------
-dontwarn org.conscrypt.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.harmony.pack200.**
-dontwarn org.objectweb.asm.**

# ---------------------------------------------------------------------------
# Guava — AppEngine + J2ObjC annotations are not on our runtime classpath
# ---------------------------------------------------------------------------
-dontwarn com.google.appengine.**
-dontwarn com.google.apphosting.**
-dontwarn com.google.j2objc.annotations.**

# ---------------------------------------------------------------------------
# Nullability / static-analysis annotations (JSR305, FindBugs, Immutables)
# ---------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.immutables.value.**

# ---------------------------------------------------------------------------
# Ktor optional blocking-IO bridge + internal atomicfu-generated mismatches
# ---------------------------------------------------------------------------
-dontwarn io.ktor.utils.io.jvm.javaio.**
-dontwarn io.ktor.network.sockets.**

# ---------------------------------------------------------------------------
# Logback's optional SMTPAppender / web integration
# ---------------------------------------------------------------------------
-dontwarn jakarta.mail.**
-dontwarn jakarta.servlet.**
-dontwarn org.codehaus.commons.compiler.**
-dontwarn org.codehaus.janino.**

# ---------------------------------------------------------------------------
# JNA — heavy reflection, keep everything
# ---------------------------------------------------------------------------
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# ---------------------------------------------------------------------------
# SLF4J / Logback reflective loading
# ---------------------------------------------------------------------------
-dontwarn org.slf4j.**
-keep class ch.qos.logback.** { *; }

# ---------------------------------------------------------------------------
# Bouncy Castle (TLS stack, uses reflection)
# ---------------------------------------------------------------------------
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# ---------------------------------------------------------------------------
# Jackson + docker-java POJOs.
# Docker-java parses the daemon's JSON into POJOs in
# com.github.dockerjava.api.model.** using Jackson reflection (@JsonProperty
# on fields/setters). Without keeping the POJOs intact, Jackson silently
# drops nested fields like Ports (List<ContainerPort>) and Labels
# (Map<String, String>) — top-level scalars still come through, which
# manifests as containers showing up but with no Compose grouping and no
# port mappings.
# ---------------------------------------------------------------------------
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class com.github.dockerjava.api.model.** { *; }
-keep class com.github.dockerjava.api.command.** { *; }
-keep class com.github.dockerjava.core.** { *; }
-keep class com.github.dockerjava.transport.** { *; }
-keep class com.github.dockerjava.httpclient5.** { *; }
-keepclassmembers class com.github.dockerjava.** {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
    @com.fasterxml.jackson.annotation.JsonProperty <methods>;
}

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.cvforge"
version = "1.0.0"

kotlin { jvmToolchain(21) }

application {
    mainClass.set("com.cvforge.server.ApplicationKt")
}

dependencies {
    implementation(project(":contract"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.forwarded.header)

    implementation(libs.exposed.java.time)
    // Cliente HTTP só para falar com a Groq
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.postgres)

    implementation(libs.jsoup)
    implementation(libs.openpdf)
    implementation(libs.jbcrypt)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }

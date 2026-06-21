plugins {
    alias(libs.plugins.kotlinJvm)
    alias(ktorLibs.plugins.ktor)
}

group = "com.tgaetaprojects.biophilia"
version = "1.0.0"
application {
    mainClass = "com.tgaetaprojects.biophilia.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.testJunit)
}
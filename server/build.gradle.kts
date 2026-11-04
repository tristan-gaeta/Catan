plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.tgaetaprojects.settledown"
version = "1.0.0"
application {
    mainClass = "com.tgaetaprojects.settledown.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(libs.sqlite.jdbc)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.websockets)
    implementation(ktorLibs.server.auth)
    // brings the JWT and key-fetching libraries used to verify Google ID tokens
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.serialization.kotlinx.json)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.client.websockets)
    testImplementation(libs.kotlin.testJunit)
}

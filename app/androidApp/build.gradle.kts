import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.app.shared)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.uiTestJunit4)
    androidTestImplementation(libs.androidx.test.services.storage)
    // Hosts the empty activity createComposeRule() renders into.
    debugImplementation(libs.androidx.compose.uiTestManifest)
    // The test storage service lets tests save screenshots that Gradle pulls back into
    // build/outputs/*_android_test_additional_output.
    androidTestUtil(libs.androidx.test.services)
}

android {
    namespace = "com.tgaetaprojects.settledown"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tgaetaprojects.settledown"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"

        // online play stays hidden in a build with no server URL
        fun quoted(property: String) = "\"${providers.gradleProperty(property).getOrElse("")}\""
        buildConfigField("String", "SERVER_URL", quoted("settledown.serverUrl"))
        buildConfigField("String", "GOOGLE_CLIENT_ID", quoted("settledown.googleClientId"))
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "DEV_USER",
                "\"${providers.gradleProperty("settledown.devUser").getOrElse("")}\""
            )
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "DEV_USER", "\"\"")
        }
    }
    testOptions {
        // Keep most UI tests on the JVM (app/shared jvmTest); androidTest is only a small on-device smoke suite.
        // Locally, run it on a physical device with connectedDebugAndroidTest. Emulators are too heavy for the dev machine,
        // so these managed devices are for CI. Automated Test Device (ATD) images are headless and stripped down.
        managedDevices {
            localDevices {
                create("atd30") {
                    device = "Small Phone"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
                create("atd34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
            groups {
                create("ci") {
                    targetDevices.add(localDevices["atd30"])
                    targetDevices.add(localDevices["atd34"])
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
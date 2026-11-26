plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        compileSdk {
            version = release(37) {
                minorApiLevel = 0
            }
        }
        minSdk = 26
        namespace = "com.cobo.app.shared"
        androidResources.enable = true
        withHostTest {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = true
            binaryOption("bundleId", "com.cobo.app.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.runtime:runtime:1.12.2")
            api("org.jetbrains.compose.foundation:foundation:1.12.2")
            api("org.jetbrains.compose.ui:ui:1.12.2")
            api("org.jetbrains.compose.animation:animation:1.12.2")
            implementation("org.jetbrains.compose.components:components-resources:1.12.2")
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            api(libs.coui.ui)
            api(libs.coui.icons)
            api(libs.coui.preference)
            api(libs.coil.compose)
            api(libs.lifecycle.runtime.compose)
            api(libs.lifecycle.viewmodel.compose)
            api(libs.navigationevent.compose)
        }
        androidMain.dependencies {
            api(libs.okhttp)
            implementation(libs.coil.network.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            api(libs.media3.exoplayer)
            api(libs.media3.exoplayer.hls)
            api(libs.media3.ui)
            implementation(libs.zxing.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.org.json)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.coil.network.ktor3)
        }
    }
}

compose.resources {
    packageOfResClass = "com.cobo.app.generated.resources"
}

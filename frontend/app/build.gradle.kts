plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cobo.app"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.cobo.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 10019
        versionName = "1.0.19-pre"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val coui = "io.github.suqi8.coui.kmp"
    // Rolling coordinate: ../coui republishes it on every commit (.githooks/publish-rolling-snapshot.sh).
    val couiVersion = "1.2.0-SNAPSHOT"

    implementation("$coui:coui-ui:$couiVersion")
    implementation("$coui:coui-icons:$couiVersion")
    implementation("$coui:coui-preference:$couiVersion")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    testImplementation("junit:junit:4.13.2")
    // 单元测试跑在 JVM 上，android.jar 里的 org.json 是空壳，换真实现才能验解析。
    testImplementation("org.json:json:20240303")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
}

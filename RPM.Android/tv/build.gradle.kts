plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rpm.player.tv"
    // Compose 1.12 (BOM 2026.09.00) cannot be compiled against an SDK older than 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rpm.player.tv"
        // Android 9. Fire OS 7, which most Fire TV sticks run, is API 28, and Google's TV
        // checklist asks for 31 or lower.
        minSdk = 28
        // Google Play requires 34 or higher for TV apps.
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // The release build signed with the debug key, for test boxes. It installs over the debug build
        // and keeps the session. It is compiled and not debuggable, like the build a listener gets. The
        // debug build's launch switches do not work in it. Build it with `assembleBox`, then run on the
        // box `cmd package compile -m speed -f com.rpm.player.tv`.
        create("box") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    testOptions {
        // The rules under test read android.view.KeyEvent constants, which the JVM stub inlines.
        // Any other framework call returns a default value instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Everything but the screens, shared with the phone app.
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    // FaceMarks asks for profile pictures with the rpm app key header.
    implementation(libs.coil.network.okhttp)
    // The sign-in QR code.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The Android phone app. Its screens follow the iPhone app (RPM.iOS), not the TV app.
// It shares only `:core` with the TV app. `:core` has no screens.
android {
    namespace = "com.rpm.player.phone"
    compileSdk = 37

    defaultConfig {
        // Same bundle id as the iPhone app: the same app on the other kind of phone.
        applicationId = "com.rpm.player"
        // Android 9, the same as the TV app and `:core`.
        minSdk = 28
        // Google Play requires at least API 36 for phone apps.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    // Finds faces in artists' photographs, so a crop keeps them in view. The model is bundled,
    // so Play services are not needed.
    implementation(libs.mlkit.face.detection)
    // Apple and Google sign-in in a Custom Tab. The iPhone app uses ASWebAuthenticationSession for this.
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
}

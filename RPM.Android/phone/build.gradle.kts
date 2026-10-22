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
            // R8 shrinks and optimises the code. Compose leans on it: a build without it carries the
            // composition tracing and the unoptimised lambdas, and scrolls at half the frame rate on the Titan.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The release build signed with the debug key, for the test phone. It installs over the debug build
        // and keeps the session, and is compiled and not debuggable, like the build a listener gets. Build it
        // with `assembleHandset`, then run on the phone `cmd package compile -m speed -f com.rpm.player`
        // (a Play install gets the same from the baseline profiles the profile installer carries).
        create("handset") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // Readable by the system profiler and `dumpsys gfxinfo` without being debuggable.
            isProfileable = true
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
    // Installs the baseline profiles that Compose and the other libraries ship, so their code is compiled
    // ahead of time on a sideloaded install too, not only on one from Google Play.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}

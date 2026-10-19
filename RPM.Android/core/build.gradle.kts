plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// What the TV app and the phone app share: sign-in, the cloud and home network clients, the library,
// playback, casting, radio, lyrics and the room, plus the string tables and the fonts. TV screens cannot
// land here by accident: androidx.tv is not a dependency of this module, so code that imports it does not
// compile in it.
android {
    namespace = "com.rpm.player.core"
    compileSdk = 37

    defaultConfig {
        // The TV's lowest supported API level. An app built on this module cannot go lower.
        minSdk = 28
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
        // The rules under test read android.view.KeyEvent constants, which the JVM stub inlines;
        // anything else from the framework answers with a default instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

// Font Awesome 5 Pro (R.font.fa_solid and R.font.fa_regular). Its licence does not allow publishing
// the files, so a checkout without them gets them here: from the private rpm-fonts-pro repo next to
// this repo when it is there, otherwise the free 5.15.4 fonts in iconfonts-free (see its README).
val iconFontsInSources = file("src/main/res/font/fa_solid.ttf").exists()
if (!iconFontsInSources) {
    val proFonts = rootDir.resolve("../../rpm-fonts-pro")
    val fontSource = if (proFonts.resolve("fa-solid-900.ttf").exists()) proFonts else file("iconfonts-free")
    val generated = layout.buildDirectory.dir("generated/iconfonts").get().asFile

    val copyIconFonts by tasks.registering(Copy::class) {
        from(fontSource) {
            include("fa-solid-900.ttf", "fa-regular-400.ttf")
            rename("fa-solid-900.ttf", "fa_solid.ttf")
            rename("fa-regular-400.ttf", "fa_regular.ttf")
        }
        into(generated.resolve("font"))
    }

    android.sourceSets.getByName("main").res.srcDir(generated)
    tasks.named("preBuild") { dependsOn(copyIconFonts) }
}

dependencies {
    // `api` for the libraries whose types are part of this module's own: the services hand out an
    // OkHttpClient, an ImageLoader, flows and Compose state, and the playback service is a Media3 session.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.coil.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.datasource.okhttp)
    // Some stations stream HLS (Radio Browser's hls=1).
    implementation(libs.androidx.media3.exoplayer.hls)
    // ALAC, AC-3 and E-AC-3 from a computer on the home network (see ffmpeg/build.gradle.kts).
    implementation(project(":ffmpeg"))
    // The rpm API serves some station logos as SVG drawings.
    implementation(libs.coil.svg)
    // Artist photos and logos for home's banner come from fanart.tv and TheAudioDB, as plain URLs.
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

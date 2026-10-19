// Media3's FFmpeg decoder extension (libraries/decoder_ffmpeg at Media3 1.11.1, Apache 2.0). Google
// does not publish a prebuilt copy. It decodes the formats that Android's own decoders do not: ALAC,
// AC-3 and E-AC-3. The FFmpeg libraries it links are built by scripts/build-android-ffmpeg.sh, outside
// this repo. The rpm.ffmpegRoot property sets where they are. Without that property the module builds
// with no native code, FfmpegLibrary.isAvailable() returns false, and those tracks play from the cloud's AAC.
plugins {
    alias(libs.plugins.android.library)
}

val ffmpegRoot: String? = providers.gradleProperty("rpm.ffmpegRoot").orNull

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("proguard-rules.txt")
        ndk {
            // Older TV boxes are 32-bit ARM. Fire TV sticks and newer boxes are 64-bit ARM.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        if (ffmpegRoot != null) {
            externalNativeBuild {
                cmake { arguments += "-DFFMPEG_ROOT=$ffmpegRoot" }
            }
        }
    }

    if (ffmpegRoot != null) {
        externalNativeBuild {
            cmake {
                path = file("src/main/jni/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.annotation)
    compileOnly(libs.checker.qual)
}

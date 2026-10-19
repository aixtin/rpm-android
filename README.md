# rpm for Android

The rpm music player for Android phones and Android TV.

Part of [rpm](https://github.com/rpmrpmrpm/rpm). Ideas and questions go to the
[discussions](https://github.com/rpmrpmrpm/rpm/discussions) there.

## What is here

The project is in `RPM.Android/`.

| Module | What it is |
| --- | --- |
| `core` | Shared by both apps: library, playback (Media3), casting, radio, the rpm server client. |
| `phone` | The phone app (Jetpack Compose). |
| `tv` | The Android TV app (Compose for TV). |
| `ffmpeg` | The Media3 FFmpeg decoder (Apache 2.0), for formats Android cannot play itself. |

## Build

You need JDK 17 and the Android SDK (Android Studio installs both). The apps run on Android 9
(API 28) or later.

```
cd RPM.Android
./gradlew :phone:assembleDebug
./gradlew :tv:assembleDebug
./gradlew test
```

Without the FFmpeg libraries the `ffmpeg` module builds empty, and the apps play the formats Android
supports itself. To include them, build them with `scripts/build-android-ffmpeg.sh` and set
`rpm.ffmpegRoot` in `~/.gradle/gradle.properties`.

The local library and playback work without an account. The cloud library, catalogue pages and
recognition need an rpm account.

## Licence

[Mozilla Public License 2.0](LICENSE). Third-party files keep their own licence. The name and logo
are reserved: see [TRADEMARKS.md](TRADEMARKS.md).

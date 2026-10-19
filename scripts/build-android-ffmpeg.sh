#!/bin/bash
# Builds the FFmpeg libraries the Android TV app's FFmpeg extension links: libavcodec, libavutil and
# libswresample, static, with only the decoders the TV needs (ALAC, AC-3, E-AC-3), for armeabi-v7a and
# arm64-v8a. The FFmpeg version and its SHA-256 come from scripts/tool-versions.txt, as for every other
# ffmpeg rpm builds.
#
# The flags follow Media3's own build_ffmpeg.sh (libraries/decoder_ffmpeg/src/main/jni, Media3 1.11.1),
# with two differences: --disable-postproc is not passed (FFmpeg 8 has no libpostproc), and the compiler
# is clang itself with --target, because the NDK's per-target names are .cmd wrappers on Windows.
#
# Runs in MSYS2 on Windows or in bash on Linux:
#   scripts/build-android-ffmpeg.sh <ndk path> <work dir>
# For example, from MSYS2 on Windows:
#   scripts/build-android-ffmpeg.sh /d/Android/Sdk/ndk/27.2.12479018 /d/Android/ffmpeg
#
# Writes <work dir>/out/<abi>/include and <work dir>/out/<abi>/lib. Set rpm.ffmpegRoot to
# <work dir>/out in the Gradle properties and the app's :ffmpeg module links them.
set -eu

NDK="$1"
WORK="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
. "$HERE/tool-versions.txt"

# The API level the libraries are built for. It must not be higher than the app's minSdk.
API=28
DECODERS="alac ac3 eac3"

case "$(uname -s)" in
    MINGW*|MSYS*) HOST=windows-x86_64; EXE=.exe ;;
    Linux) HOST=linux-x86_64; EXE= ;;
    Darwin) HOST=darwin-x86_64; EXE= ;;
    *) echo "unknown host $(uname -s)"; exit 1 ;;
esac
TOOLS="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
[ -x "$TOOLS/clang$EXE" ] || { echo "no clang at $TOOLS"; exit 1; }

SOURCE="$WORK/ffmpeg-$FFMPEG_VERSION"
if [ ! -d "$SOURCE" ]; then
    archive="$WORK/ffmpeg-$FFMPEG_VERSION.tar.xz"
    curl -sL -o "$archive" "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz"
    echo "$FFMPEG_SHA256  $archive" | sha256sum -c -
    tar -xf "$archive" -C "$WORK"
fi

COMMON="
    --target-os=android
    --enable-cross-compile
    --enable-static
    --disable-shared
    --disable-doc
    --disable-programs
    --disable-everything
    --disable-avdevice
    --disable-avformat
    --disable-swscale
    --disable-avfilter
    --disable-symver
    --enable-swresample
    --extra-ldexeflags=-pie
    --disable-v4l2-m2m
    --disable-vulkan
    --cc=$TOOLS/clang$EXE
    --nm=$TOOLS/llvm-nm$EXE
    --ar=$TOOLS/llvm-ar$EXE
    --ranlib=$TOOLS/llvm-ranlib$EXE
    --strip=$TOOLS/llvm-strip$EXE
    "
for decoder in $DECODERS; do COMMON="$COMMON --enable-decoder=$decoder"; done

build() {
    local abi="$1" arch="$2" cpu="$3" target="$4" cflags="$5" ldflags="$6"
    local dir="$WORK/build/$abi"
    rm -rf "$dir"
    mkdir -p "$dir"
    (
        cd "$dir"
        # shellcheck disable=SC2086
        "$SOURCE/configure" \
            --prefix="$WORK/out/$abi" \
            --arch="$arch" \
            --cpu="$cpu" \
            --extra-cflags="--target=$target$API $cflags" \
            --extra-ldflags="--target=$target$API $ldflags" \
            $COMMON
        make -j"$(nproc 2>/dev/null || echo 4)"
        make install-libs install-headers
    )
}

build armeabi-v7a arm armv7-a armv7a-linux-androideabi "-march=armv7-a -mfloat-abi=softfp" "-Wl,--fix-cortex-a8"
build arm64-v8a aarch64 armv8-a aarch64-linux-android "" ""

echo "Built FFmpeg $FFMPEG_VERSION ($DECODERS) into $WORK/out"

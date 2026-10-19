#
# Copyright 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Media3 1.11.1's CMakeLists.txt for the FFmpeg extension, changed for rpm: the FFmpeg libraries and
# headers are read from FFMPEG_ROOT/<abi> (what scripts/build-android-ffmpeg.sh installs, outside the
# repo), not from an ffmpeg folder inside the source tree.

cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

set(CMAKE_CXX_STANDARD 11)

project(libffmpegJNI C CXX)

if(NOT DEFINED FFMPEG_ROOT)
    message(FATAL_ERROR "FFMPEG_ROOT is not set; set rpm.ffmpegRoot in the Gradle properties")
endif()
set(ffmpeg_location "${FFMPEG_ROOT}/${ANDROID_ABI}")

foreach(ffmpeg_lib avutil swresample avcodec)
    add_library(${ffmpeg_lib} STATIC IMPORTED)
    set_target_properties(${ffmpeg_lib} PROPERTIES IMPORTED_LOCATION "${ffmpeg_location}/lib/lib${ffmpeg_lib}.a")
endforeach()

include_directories("${ffmpeg_location}/include")
find_library(android_log_lib log)

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)

set_target_properties(ffmpegJNI PROPERTIES
                      CXX_VISIBILITY_PRESET hidden
                      VISIBILITY_INLINES_HIDDEN ON)

target_link_libraries(ffmpegJNI
                      PRIVATE android
                      PRIVATE swresample
                      PRIVATE avcodec
                      PRIVATE avutil
                      PRIVATE ${android_log_lib})

# Needed for arm64-v8a from NDK 23.1.7779620 on (google/ExoPlayer#9933).
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()

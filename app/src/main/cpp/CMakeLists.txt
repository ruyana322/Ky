cmake_minimum_required(VERSION 3.22.1)
project("kythera_video_ai")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── native-lib ──────────────────────────────────────────────────────────────
add_library(
    native-lib
    SHARED
    native-lib.cpp
)

# Android system libs
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(mediandk-lib mediandk)

target_link_libraries(
    native-lib
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    ${mediandk-lib}
)

# ── Placeholder: FFmpeg & NCNN will be linked here after you add prebuilt .so ──
# Example (uncomment after adding prebuilt libs):
# add_library(ffmpeg SHARED IMPORTED)
# set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION
#     ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libffmpeg.so)
# add_library(ncnn STATIC IMPORTED)
# set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION
#     ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.a)

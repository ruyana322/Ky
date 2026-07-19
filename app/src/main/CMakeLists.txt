cmake_minimum_required(VERSION 3.22.1)
project("kythera_native")

# ─── CMakeLists.txt ini ada di app/src/main/ ──────────────────────────────────
# CMAKE_SOURCE_DIR = app/src/main/
# JNI_LIBS_DIR path relatif ke project root

set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../../../jniLibs/${ANDROID_ABI})

# ─── Import libMNN.so ─────────────────────────────────────────────────────────
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN.so
)

# ─── Import libopencv_java4.so ────────────────────────────────────────────────
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopencv_java4.so
)

# ─── MNN Video Bridge ─────────────────────────────────────────────────────────
add_library(kythera_mnn SHARED
    cpp/MnnVideoBridge.cpp
)
target_include_directories(kythera_mnn PRIVATE
    ${CMAKE_SOURCE_DIR}/cpp/include
    ${CMAKE_SOURCE_DIR}/cpp/include/MNN
)
target_link_libraries(kythera_mnn
    MNN
    android
    jnigraphics
    log
)

# ─── OpenCV Video Bridge ──────────────────────────────────────────────────────
add_library(kythera_cv SHARED
    cpp/OpenCvBridge.cpp
)
target_include_directories(kythera_cv PRIVATE
    ${CMAKE_SOURCE_DIR}/cpp/include
    ${CMAKE_SOURCE_DIR}/cpp/include/opencv2
)
target_link_libraries(kythera_cv
    opencv_java4
    android
    jnigraphics
    log
)

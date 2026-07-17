cmake_minimum_required(VERSION 3.22.1)
project("kythera_mnn")

# ── Import libMNN.so dari assets (dikopi ke jniLibs saat build) ──────────────
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libMNN.so
)

add_library(mnn_opencl_runtime SHARED IMPORTED)
set_target_properties(mnn_opencl_runtime PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libMNN_CL.so
)

# ── JNI Bridge ────────────────────────────────────────────────────────────────
add_library(
    kythera_mnn
    SHARED
    cpp/MnnVideoBridge.cpp
)

# Include MNN headers (download MNN source, ambil include/ folder)
target_include_directories(kythera_mnn PRIVATE
    ${CMAKE_SOURCE_DIR}/cpp/include/MNN
)

# Link libraries
target_link_libraries(kythera_mnn
    MNN
    android
    jnigraphics   # untuk AndroidBitmap
    log
)

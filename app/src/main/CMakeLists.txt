cmake_minimum_required(VERSION 3.10.2)
project("kythera_engine")

# Optimization flags — match PixelHD: O3 + OpenMP
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++14 -O3 -fopenmp")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -O3 -fopenmp")

# ─────────────────────────────────────────────────────────────────────────────
# 1. NCNN-Vulkan static libraries
# ─────────────────────────────────────────────────────────────────────────────
set(NCNN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/Ncnn-vulkan)
include_directories(${NCNN_DIR}/${ANDROID_ABI}/include/ncnn)

add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION
    ${NCNN_DIR}/${ANDROID_ABI}/lib/libncnn.a)

add_library(glslang STATIC IMPORTED)
set_target_properties(glslang PROPERTIES IMPORTED_LOCATION
    ${NCNN_DIR}/${ANDROID_ABI}/lib/libglslang.a)

add_library(SPIRV STATIC IMPORTED)
set_target_properties(SPIRV PROPERTIES IMPORTED_LOCATION
    ${NCNN_DIR}/${ANDROID_ABI}/lib/libSPIRV.a)

add_library(glslang_default_resource_limits STATIC IMPORTED)
set_target_properties(glslang_default_resource_limits PROPERTIES IMPORTED_LOCATION
    ${NCNN_DIR}/${ANDROID_ABI}/lib/libglslang-default-resource-limits.a)

# ─────────────────────────────────────────────────────────────────────────────
# 2. Additional includes (OpenCV headers for OpenCvBridge)
# ─────────────────────────────────────────────────────────────────────────────
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/cpp/include)

# ─────────────────────────────────────────────────────────────────────────────
# 3. System Android libraries
# ─────────────────────────────────────────────────────────────────────────────
find_library(log-lib       log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib   android)
find_library(vulkan-lib    vulkan)

# OpenCV .so (used by opencv_bridge only)
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libopencv_java4.so)

# ─────────────────────────────────────────────────────────────────────────────
# 4. PRIMARY: librealesrganappncnn.so
#    Contains RealEsrganBridge + FaceRestoreBridge + legacy NcnnVideoBridge stubs
# ─────────────────────────────────────────────────────────────────────────────
add_library(realesrganappncnn SHARED
    cpp/NcnnVideoBridge.cpp)

target_link_libraries(realesrganappncnn
    ncnn
    glslang
    SPIRV
    glslang_default_resource_limits
    ${vulkan-lib}
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib})

# ─────────────────────────────────────────────────────────────────────────────
# 5. OpenCV Bridge (video metadata extraction)
# ─────────────────────────────────────────────────────────────────────────────
add_library(opencv_bridge SHARED
    cpp/OpenCvBridge.cpp)

target_link_libraries(opencv_bridge
    opencv_java4
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib})

# ─────────────────────────────────────────────────────────────────────────────
# 6. MNN Bridge stub (keeps Kotlin MnnVideoBridge.kt from crashing on load)
#    Empty stub — MNN logic removed but .so still expected by System.loadLibrary
# ─────────────────────────────────────────────────────────────────────────────
add_library(mnn_bridge SHARED
    cpp/MnnVideoBridge.cpp)

target_link_libraries(mnn_bridge
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib})

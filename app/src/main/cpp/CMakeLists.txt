cmake_minimum_required(VERSION 3.22.1)
project("kythera_video_ai")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 1. ── MESIN LAMA (FFmpeg) ──
add_library(
    native-lib
    SHARED
    native-lib.cpp
)

# 2. ── IMPORT MESIN AI NCNN TENCENT (JALUR RESMI) ──
# Kasih tau lokasi buku panduan CMake dari folder NCNN yang baru diekstrak
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# =================================================================
# OBAT PAMUNGKAS: Paksa masukin alamat otaknya biar C++ nggak buta
# =================================================================
include_directories(${CMAKE_SOURCE_DIR}/ncnn/${ANDROID_ABI}/include)

# 3. ── MESIN BARU KHUSUS POTO (kythera_ai) ──
add_library(
    kythera_ai
    SHARED
    realsr.cpp
)

# Siapin alat bantu bawaan dari sistem Android
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)
find_library(mediandk-lib mediandk)

# 4. ── SAMBUNGIN MESIN LAMA ──
target_link_libraries(
    native-lib
    ${log-lib}
    ${android-lib}
)

# 5. ── SAMBUNGIN MESIN BARU & NCNN ──
# Cukup panggil "ncnn" di sini, dia otomatis bawa temen-temennya (Vulkan, OpenMP, glslang)
target_link_libraries(
    kythera_ai
    ncnn
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    ${mediandk-lib}
)

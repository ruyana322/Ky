# Versi minimum CMake bawaan Android Studio
cmake_minimum_required(VERSION 3.10.2)

# Nama proyek C++ lu
project("kythera_engine")

# Aktifin flag OpenMP dan optimasi level 3 (-O3) biar mesin AI lari sekencang mungkin
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -fopenmp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fopenmp")

# ==========================================
# 1. SETUP MESIN NCNN & PENERJEMAH VULKAN
# ==========================================
set(NCNN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/Ncnn-vulkan)
include_directories(${NCNN_DIR}/${ANDROID_ABI}/include/ncnn)

# A. Mesin Utama NCNN
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_DIR}/${ANDROID_ABI}/lib/libncnn.a)

# B. Komponen Penerjemah glslang & SPIRV
add_library(glslang STATIC IMPORTED)
set_target_properties(glslang PROPERTIES IMPORTED_LOCATION ${NCNN_DIR}/${ANDROID_ABI}/lib/libglslang.a)

add_library(SPIRV STATIC IMPORTED)
set_target_properties(SPIRV PROPERTIES IMPORTED_LOCATION ${NCNN_DIR}/${ANDROID_ABI}/lib/libSPIRV.a)

# ==========================================
# 2. SETUP OPENCV
# ==========================================
# A. Header OpenCV
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/cpp/include)

# B. Mesin Utama OpenCV (Ini yang bikin error tadi, sekarang udah ditambahin)
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/jniLibs/${ANDROID_ABI}/libopencv_java4.so)

# ==========================================
# 3. PANGGIL LIBRARY BAWAAN ANDROID
# ==========================================
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics) 
find_library(android-lib android)         
find_library(vulkan-lib vulkan)           

# ==========================================
# 4. RAKIT SEMUA JEMBATAN C++
# ==========================================

# A. Merakit Jembatan NCNN
add_library(ncnn_bridge SHARED cpp/NcnnVideoBridge.cpp)
target_link_libraries(ncnn_bridge
                      ncnn
                      glslang
                      SPIRV
                      ${vulkan-lib}
                      ${log-lib}
                      ${jnigraphics-lib}
                      ${android-lib})

# B. Merakit Jembatan OpenCV (Sekarang dilink ke mesin opencv_java4)
add_library(opencv_bridge SHARED cpp/OpenCvBridge.cpp)
target_link_libraries(opencv_bridge
                      opencv_java4
                      ${log-lib}
                      ${jnigraphics-lib}
                      ${android-lib})

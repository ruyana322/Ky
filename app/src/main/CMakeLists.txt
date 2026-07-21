# Versi minimum CMake bawaan Android Studio
cmake_minimum_required(VERSION 3.10.2)

# Nama proyek C++ lu
project("kythera_engine")

# Aktifin flag OpenMP dan optimasi level 3 (-O3) biar mesin AI lari sekencang mungkin
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -fopenmp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fopenmp")

# ==========================================
# 1. SETUP MESIN NCNN VULKAN (Arahkan ke folder cpp/)
# ==========================================
set(NCNN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cpp/Ncnn-vulkan)
include_directories(${NCNN_DIR}/${ANDROID_ABI}/include/ncnn)

add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION
    ${NCNN_DIR}/${ANDROID_ABI}/lib/libncnn.a)

# ==========================================
# 2. SETUP HEADER OPENCV (Arahkan ke folder cpp/)
# ==========================================
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/cpp/include)

# ==========================================
# 3. PANGGIL LIBRARY BAWAAN ANDROID
# ==========================================
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics) 
find_library(android-lib android)         
find_library(vulkan-lib vulkan)           

# ==========================================
# 4. RAKIT SEMUA JEMBATAN C++ (Arahkan ke folder cpp/)
# ==========================================

# A. Merakit Jembatan NCNN
add_library(ncnn_bridge SHARED cpp/NcnnVideoBridge.cpp)
target_link_libraries(ncnn_bridge
                      ncnn
                      ${vulkan-lib}
                      ${log-lib}
                      ${jnigraphics-lib}
                      ${android-lib})

# B. Merakit Jembatan OpenCV
add_library(opencv_bridge SHARED cpp/OpenCvBridge.cpp)
target_link_libraries(opencv_bridge
                      ${log-lib}
                      ${jnigraphics-lib}
                      ${android-lib})

# C. Merakit Jembatan MNN Lama
add_library(mnn_bridge SHARED cpp/MnnVideoBridge.cpp)
target_link_libraries(mnn_bridge
                      ${log-lib}
                      ${jnigraphics-lib}
                      ${android-lib})

# Versi minimum CMake bawaan Android Studio
cmake_minimum_required(VERSION 3.10.2)

# Nama proyek C++ lu
project("kythera_ncnn")

# Aktifin flag OpenMP dan optimasi level 3 (-O3) biar mesin AI lari sekencang mungkin
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -O3 -fopenmp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fopenmp")

# 1. Path ke folder mesin NCNN yang udah lu dietin (sejajar sama file ini)
set(NCNN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/Ncnn-vulkan)

# 2. Kasih tau CMake di mana letak file header (.h) nya NCNN
include_directories(${NCNN_DIR}/${ANDROID_ABI}/include/ncnn)

# 3. Import mesin inti NCNN (.a) berdasarkan arsitektur HP (arm64-v8a)
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION
    ${NCNN_DIR}/${ANDROID_ABI}/lib/libncnn.a)

# 4. Cari library bawaan Android yang wajib dipakai
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics) # Buat ngolah gambar/Bitmap
find_library(android-lib android)         # Buat baca file model dari folder assets
find_library(vulkan-lib vulkan)           # Mesin penggerak GPU!

# 5. Daftarin file C++ lu (Jembatannya)
# (Gua kasih nama NcnnVideoBridge.cpp, pastikan file ini ada di folder cpp lu)
add_library(ncnn_bridge SHARED
            NcnnVideoBridge.cpp)

# 6. Ikat semuanya jadi satu kesatuan
target_link_libraries(ncnn_bridge
                      ncnn
                      ${vulkan-lib}
                      ${log-lib}
                      ${jnigraphics-lib}
                      ${android-lib})

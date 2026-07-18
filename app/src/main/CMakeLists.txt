cmake_minimum_required(VERSION 3.22.1)
project("kythera_native")

# ─── Paths ────────────────────────────────────────────────────────────────────
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# ─── Import libMNN.so ─────────────────────────────────────────────────────────
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/libMNN.so
)

# ─── Import libopencv_java4.so ────────────────────────────────────────────────
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/libopencv_java4.so
)

# ─── MNN Video Bridge ─────────────────────────────────────────────────────────
add_library(kythera_mnn SHARED MnnVideoBridge.cpp)
target_include_directories(kythera_mnn PRIVATE ${CMAKE_SOURCE_DIR}/include/MNN)
target_link_libraries(kythera_mnn MNN android jnigraphics log)

# ─── OpenCV Video Bridge ──────────────────────────────────────────────────────
add_library(kythera_cv SHARED OpenCvBridge.cpp)
target_include_directories(kythera_cv PRIVATE ${CMAKE_SOURCE_DIR}/include/opencv4)
target_link_libraries(kythera_cv opencv_java4 android jnigraphics log)

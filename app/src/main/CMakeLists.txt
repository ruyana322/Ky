cmake_minimum_required(VERSION 3.22.1)
project("kythera_native")

# CMAKE_SOURCE_DIR = app/src/main/
# Naik 3 level: main → src → app
# Lalu masuk jniLibs/

set(JNILIBS_DIR ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI})

# ─── Verify path (debug) ──────────────────────────────────────────────────────
message(STATUS "JNI libs dir: ${JNILIBS_DIR}")

# ─── Import libMNN.so ─────────────────────────────────────────────────────────
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION ${JNILIBS_DIR}/libMNN.so
)

# ─── Import libopencv_java4.so ────────────────────────────────────────────────
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${JNILIBS_DIR}/libopencv_java4.so
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

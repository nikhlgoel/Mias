cmake_minimum_required(VERSION 3.22.1)

project("mias_inference")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── llama.cpp as static library ─────────────────────────────
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Enable ARM NEON (critical for performance on Android) 
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

add_subdirectory(llama.cpp)

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Define native bridge library
add_library(
        mias_inference
        SHARED
        mias_jni_bridge.cpp
)

# ── 16 KB page size support ─────────────────────────────────
# Required for Play Store submissions targeting Android 15+ from
# November 2025 onward, and for compatibility with Pixel 8+ and
# other devices running with 16 KB memory pages.
# https://developer.android.com/guide/practices/page-sizes
target_link_options(mias_inference PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)

# Connect libraries to our bridge
target_link_libraries(
        mias_inference
        llama
        ggml
        ${log-lib}
        ${android-lib}
)

target_include_directories(mias_inference PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
)

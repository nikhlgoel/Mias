cmake_minimum_required(VERSION 3.22.1)

project("litert_lm")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Android libraries
find_library(log-lib log)

# Define native bridge library
add_library(
        litert_lm
        SHARED
        litert_jni_bridge.cpp
)

# Connect libraries to our bridge
target_link_libraries(
        litert_lm
        ${log-lib}
)

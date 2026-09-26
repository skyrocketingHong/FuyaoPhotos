# The vendored x265 encoder ships for the 64-bit ABIs; 32-bit builds get a stub
# library so every APK split still loads and the Kotlin layer falls back to the
# platform encoder. Debug native builds still optimize x265 at -O2: a -O0 build
# of the encoder is unusably slow.
cmake_minimum_required(VERSION 3.22)
project(fuyao_hevc)
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
    set(ENABLE_CLI OFF CACHE BOOL "" FORCE)
    set(ENABLE_LIBNUMA OFF CACHE BOOL "" FORCE)
    # x86 assembly needs nasm, which the SDK toolchain does not provide.
    set(ENABLE_ASSEMBLY OFF CACHE BOOL "" FORCE)
    string(REPLACE "-O0" "-O2" CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG}")
    string(REPLACE "-O0" "-O2" CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG}")
    add_subdirectory(x265 x265_build)
    add_library(fuyao_hevc SHARED fuyao_hevc.cpp)
    # x265's static target carries a hyphen, so link the object libraries it is
    # built from and add the source and generated headers explicitly.
    target_include_directories(fuyao_hevc PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/x265
        ${CMAKE_CURRENT_BINARY_DIR}/x265_build)
    target_link_libraries(fuyao_hevc encoder common ${CMAKE_DL_LIBS} log)
    if(TARGET dynamicHDR10)
        target_link_libraries(fuyao_hevc dynamicHDR10)
    endif()
else()
    add_library(fuyao_hevc SHARED fuyao_stub.cpp)
    target_link_libraries(fuyao_hevc log)
endif()

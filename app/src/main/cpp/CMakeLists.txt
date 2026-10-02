cmake_minimum_required(VERSION 3.22.1)
project(librehufm CXX)

# MediaTek FM JNI from AOSP packages/apps/FMRadio (Apache 2.0, via LineageOS lineage-16.0), patched for the NDK
# and for devices without libfmcust.so. See fmr/README.md.
add_library(librehufm SHARED
    fmr/fmr_core.cpp
    fmr/fmr_err.cpp
    fmr/libfm_jni.cpp
    fmr/common.cpp)
target_compile_options(librehufm PRIVATE -Wno-unused-parameter -Wno-unused-variable -Wno-format)
target_link_libraries(librehufm log dl)

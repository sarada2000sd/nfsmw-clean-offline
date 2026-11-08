cmake_minimum_required(VERSION 3.22)
project(NFSMWAndroidBootstrap C CXX)
if(NOT ANDROID OR NOT (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a"))
    message(FATAL_ERROR "This target requires Android arm64-v8a or armeabi-v7a")
endif()

add_library(native_app_glue STATIC "${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c")
target_include_directories(native_app_glue PUBLIC "${ANDROID_NDK}/sources/android/native_app_glue")

get_filename_component(NFS_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
set(PCRECOMP "${NFS_ROOT}/../pcrecomp")
# Shared DXVK/ReXGlue infrastructure from the sibling NFSU2 checkout.
set(NFSU2_ROOT "${NFS_ROOT}/../nfsu2-recompiled" CACHE PATH "nfsu2-recompiled checkout")
set(DXVK_SOURCE "${NFSU2_ROOT}/build/dxvk-source" CACHE PATH "Prepared DXVK native source")
set(DXVK_ANDROID_STATIC ON CACHE BOOL "Link into application" FORCE)
add_subdirectory("${NFSU2_ROOT}/ports/dxvk_android" "${CMAKE_CURRENT_BINARY_DIR}/dxvk")

# Translated PC Speed.exe code generated locally from the user's own copy.
set(GEN_DIR "${NFS_ROOT}/src/recomp/gen")
file(GLOB GEN_SOURCES CONFIGURE_DEPENDS "${GEN_DIR}/recomp_*.c")
if(NOT GEN_SOURCES)
    message(FATAL_ERROR "Missing generated Speed.exe code in ${GEN_DIR}; prepare the owner's local game files first")
endif()

# D3DX9 is imported by Speed.exe and its effect/math routines are needed by
# single-player graphics. Keep this locally generated module in the APK.
set(D3DX_GEN_DIR "${NFS_ROOT}/src/recomp/gen_d3dx")
file(GLOB D3DX_GEN_SOURCES CONFIGURE_DEPENDS "${D3DX_GEN_DIR}/d3dx_recomp_*.c")
if(NOT D3DX_GEN_SOURCES)
    message(FATAL_ERROR "Missing lifted d3dx9_26 code in ${D3DX_GEN_DIR}")
endif()
list(APPEND ANDROID_GEN_SOURCES ${D3DX_GEN_SOURCES})

# Sound-bank routines are extracted from the owner's local Sound/*.abk files.
set(ABK_GEN_DIR "${NFS_ROOT}/src/recomp/gen_abk")
file(GLOB ABK_GEN_SOURCES CONFIGURE_DEPENDS "${ABK_GEN_DIR}/abk_*.c")
if(NOT ABK_GEN_SOURCES)
    message(FATAL_ERROR "Missing lifted sound-bank code in ${ABK_GEN_DIR}")
endif()
list(APPEND ANDROID_GEN_SOURCES ${ABK_GEN_SOURCES})
set(ABK_BLOCKS_C "${NFS_ROOT}/work/abk_blocks.c")
if(NOT EXISTS "${ABK_BLOCKS_C}")
    message(FATAL_ERROR "Missing generated ABK block data at ${ABK_BLOCKS_C}; run scripts/abk_module.py with the local game files")
endif()

set_property(GLOBAL PROPERTY JOB_POOLS guest_compile=3)
add_library(nfs_guest OBJECT ${ANDROID_GEN_SOURCES})
set_property(TARGET nfs_guest PROPERTY JOB_POOL_COMPILE guest_compile)
target_include_directories(nfs_guest PRIVATE "${PCRECOMP}/runtime/recomp32" "${GEN_DIR}" "${D3DX_GEN_DIR}" "${ABK_GEN_DIR}" "${NFS_ROOT}/src/runtime")
target_compile_definitions(nfs_guest PRIVATE RECOMP_STRICT_ICALL RECOMP_LOCAL_REGS RECOMP_X87_WINDOW "RECOMP_MEM_QUAL=")
target_compile_options(nfs_guest PRIVATE -O2 -w -fwrapv -fno-strict-aliasing -fexceptions -ffp-contract=off
    -include "${CMAKE_CURRENT_SOURCE_DIR}/arm64_guest.h")
target_compile_options(nfs_guest PRIVATE -g0 -fvisibility=hidden)

option(NFS_GUEST_LTO "ThinLTO over the lifted game code" ON)
if(NFS_GUEST_LTO)
    target_compile_options(nfs_guest PRIVATE -flto=thin)
endif()

add_library(nfsmw_android SHARED
    main.cpp
    guest_runtime.cpp
    d3d9_bridge.cpp
    "${NFS_ROOT}/src/runtime/overrides.c"
    "${NFS_ROOT}/src/runtime/widescreen.c"
    "${NFS_ROOT}/src/runtime/abk_code.c"
    "${ABK_BLOCKS_C}"
    $<TARGET_OBJECTS:nfs_guest>)

target_include_directories(nfsmw_android SYSTEM PRIVATE
    "${DXVK_SOURCE}/include/native"
    "${DXVK_SOURCE}/include/native/windows"
    "${DXVK_SOURCE}/include/native/directx")
target_include_directories(nfsmw_android PRIVATE
    "${PCRECOMP}/runtime/recomp32"
    "${PCRECOMP}/runtime/win32hle"
    "${NFS_ROOT}/src/runtime"
    "${NFSU2_ROOT}/src/runtime")

# All WinSock imports remain bound to local ABI-compatible stubs. In this
# build socket creation and external DNS are disabled; single-player stays local.
target_compile_definitions(nfsmw_android PRIVATE
    RECOMP_STRICT_ICALL
    RECOMP_X87_WINDOW
    NFS_OFFLINE_ONLY
    VK_USE_PLATFORM_ANDROID_KHR)

target_compile_features(nfsmw_android PRIVATE cxx_std_17)
target_compile_options(nfsmw_android PRIVATE
    -O2 -fno-strict-aliasing -ffp-contract=off -Wall -Wextra -Werror
    -Wno-missing-field-initializers)
target_link_options(nfsmw_android PRIVATE
    "-uANativeActivity_onCreate"
    "-Wl,-z,max-page-size=16384")
target_link_libraries(nfsmw_android PRIVATE
    native_app_glue android log vulkan dxvk_android aaudio dl)

if(NFS_GUEST_LTO)
    target_link_options(nfsmw_android PRIVATE
        -flto=thin
        "-Wl,--thinlto-jobs=3"
        "-Wl,--thinlto-cache-dir=${CMAKE_BINARY_DIR}/thinlto-cache")
endif()

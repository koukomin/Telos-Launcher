cmake_minimum_required(VERSION 3.18...4.0)

project(baresip)

add_link_options("LINKER:--build-id=none" "-Wl,-z,max-page-size=16384")

# Static libraries produced by sip-native/Makefile (published by the "Build SIP native libraries" workflow)
set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../../../sip-native/distribution)

function(import_static name dir file)
    add_library(lib_${name} STATIC IMPORTED)
    set_target_properties(lib_${name} PROPERTIES IMPORTED_LOCATION
            ${distribution_DIR}/${dir}/lib/${ANDROID_ABI}/${file})
endfunction()

import_static(crypto openssl libcrypto.a)
import_static(ssl openssl libssl.a)
import_static(re re libre.a)
import_static(opus opus libopus.a)
import_static(baresip baresip libbaresip.a)

add_library(baresip SHARED ${CMAKE_SOURCE_DIR}/baresip.c)

target_include_directories(baresip PRIVATE
        ${distribution_DIR}/openssl/include
        ${distribution_DIR}/re/include
        ${distribution_DIR}/baresip/include)

add_definitions(-DHAVE_PTHREAD)

target_link_libraries(
        baresip
        android
        aaudio
        lib_baresip
        lib_re
        lib_ssl
        lib_crypto
        lib_opus
        z
        log)

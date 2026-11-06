cmake_minimum_required(VERSION 3.22)
project("telos_shizuku")
set(CMAKE_CXX_STANDARD 17)
add_link_options(-Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
add_compile_options(-fdata-sections -ffunction-sections -fno-exceptions -fno-rtti)
add_executable(libshizuku.so
        starter.cpp misc.cpp selinux.cpp cgroup.cpp android.cpp)
target_link_libraries(libshizuku.so log)

# Pairing is SPAKE2 over TLS, not part of the starter executable.
find_package(boringssl REQUIRED CONFIG)
add_library(adb SHARED adb_pairing.cpp)
target_link_libraries(adb log boringssl::crypto_static)

# The server initializes Rish during construction even without a terminal UI.
set(RISH_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../rish/src/main/cpp)
add_library(rish SHARED
        ${RISH_SRC}/main.cpp ${RISH_SRC}/pts.cpp
        ${RISH_SRC}/rikka_rish_RishTerminal.cpp ${RISH_SRC}/rikka_rish_RishHost.cpp)
target_link_libraries(rish log)

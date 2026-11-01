cmake_minimum_required(VERSION 3.22)
project("telos_shizuku")
set(CMAKE_CXX_STANDARD 17)
add_compile_options(-fdata-sections -ffunction-sections -fno-exceptions -fno-rtti)
add_executable(libshizuku.so
        starter.cpp misc.cpp selinux.cpp cgroup.cpp android.cpp)
target_link_libraries(libshizuku.so log)

cmake_minimum_required(VERSION 3.22.1)

project(ktpwarp_node_bridge)

add_library(native-lib SHARED
        src/main/cpp/native-lib.cpp)

target_include_directories(native-lib PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/libnode/include/node)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so)

find_library(log-lib log)

target_link_libraries(native-lib
        node
        ${log-lib})

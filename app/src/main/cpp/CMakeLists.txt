cmake_minimum_required(VERSION 3.22.1)
project(dsmusic_mp3 LANGUAGES C CXX)

find_package(lame REQUIRED CONFIG)

set(LAME_LIBRARY_TARGET "")
foreach(candidate
    lame::lame
    lame::libmp3lame
    lame::libmp3lame.so
    lame::libmp3lame.a
)
    if(TARGET ${candidate})
        set(LAME_LIBRARY_TARGET ${candidate})
        break()
    endif()
endforeach()

if(LAME_LIBRARY_TARGET STREQUAL "")
    message(FATAL_ERROR "The LAME Prefab package did not expose a supported library target")
endif()

add_library(dsmusic_mp3 SHARED native_mp3_encoder.cpp)

if(TARGET lame::headers)
    target_link_libraries(dsmusic_mp3 PRIVATE lame::headers)
endif()

target_compile_features(dsmusic_mp3 PRIVATE cxx_std_17)
target_link_libraries(dsmusic_mp3 PRIVATE ${LAME_LIBRARY_TARGET} log)

cmake_minimum_required(VERSION 3.22.1)

project("claurst_pty" C)

add_library(
    claurst-pty
    SHARED
    pty_helper.c
)

find_library(log-lib log)

target_link_libraries(
    claurst-pty
    ${log-lib}
)

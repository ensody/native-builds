include("$ENV{VCPKG_ROOT}/scripts/toolchains/linux.cmake")

set(CMAKE_SYSROOT "$ENV{TOOLCHAIN_DIR}/$ENV{TOOLCHAIN_TARGET}/sysroot" CACHE STRING "" FORCE)

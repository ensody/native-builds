include("$ENV{VCPKG_ROOT}/scripts/toolchains/linux.cmake")

set(_TOOLCHAIN_BASE "$ENV{TOOLCHAIN_DIR}/$ENV{TOOLCHAIN_TARGET}" CACHE STRING "" FORCE)
set(CMAKE_SYSROOT "${_TOOLCHAIN_BASE}/sysroot" CACHE STRING "" FORCE)

if (NOT _VCPKG_LINUX_TOOLCHAIN_OVERRIDE)
  set(_VCPKG_LINUX_TOOLCHAIN_OVERRIDE 1)

  # It's not sufficient to set just CMAKE_SYSROOT. Make sure that the sysroot is actually used instead of the host's variant.
  string(PREPEND CMAKE_C_FLAGS " --sysroot=${CMAKE_SYSROOT} -isystem ${CMAKE_SYSROOT}/usr/include -isystem ${_TOOLCHAIN_BASE}/lib/*/$ENV{TOOLCHAIN_TARGET}/*/include ")
  string(PREPEND CMAKE_CC_FLAGS " --sysroot=${CMAKE_SYSROOT} -isystem ${CMAKE_SYSROOT}/usr/include -isystem ${_TOOLCHAIN_BASE}/lib/*/$ENV{TOOLCHAIN_TARGET}/*/include ")
  string(PREPEND CMAKE_AR_FLAGS " --sysroot=${CMAKE_SYSROOT} ")
  string(PREPEND CMAKE_LINKER_FLAGS " --sysroot=${CMAKE_SYSROOT} ")

  if (VCPKG_TARGET_ARCHITECTURE STREQUAL "arm64")
    # Disable outline-atomics since this is too new for the Kotlin Native toolchain
    string(APPEND CMAKE_C_FLAGS " -mno-outline-atomics ")
    string(APPEND CMAKE_CXX_FLAGS " -mno-outline-atomics ")
  endif ()
endif ()

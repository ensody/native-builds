# Changelog

## 0.8.2

* Fixed Android JNI builds making duplicate copies of NativeBuilds .so libraries.

## 0.8.1

* Generate CMakeLists.txt during Gradle sync, so the IDE won't complain about missing files.

## 0.8.0

* Added Zig based cross-compilation for JNI C++ code (JVM & Android). Use `jniNativeBuild()` in your `build.gradle.kts` to compile your C++ code for all platforms at once.
* The headers for all platforms are now combined into a single .zip file with deduplicated "common" folder and platform-specific extra files (e.g. in OpenSSL, openssl/configuration.h is platform-specific, but all other headers are identical). This also speeds up downloading the dependencies.

## 0.7.0

* Added `substituteAndroidNativeLibsInUnitTests()` which can be called in your `build.gradle.kts` in order to automatically replace Android JNI dependencies with JVM JNI dependencies within your unit tests.

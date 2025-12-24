# Native builds of C/C++ libraries for Kotlin Multiplatform

[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/nativebuilds-gradle-plugin?label=nativebuilds-gradle-plugin&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/nativebuilds-gradle-plugin)

This project regularly builds the latest available vcpkg version of the following libraries:

* **OpenSSL** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/openssl-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/openssl-headers)
  * `com.ensody.nativebuilds:openssl-headers`: headers
  * `com.ensody.nativebuilds:openssl-libcrypto`: core crypto library
  * `com.ensody.nativebuilds:openssl-libssl`: TLS library (optional; depends on libcrypto)
* **curl** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/curl-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/curl-headers)
  * `com.ensody.nativebuilds:curl-headers`: headers
  * `com.ensody.nativebuilds:curl-libcurl`: static library (depends on openssl, nghttp2, nghttp3, ngtcp2, zlib)
* **nghttp2** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/nghttp2-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/nghttp2-headers)
  * `com.ensody.nativebuilds:nghttp2-headers`: headers
  * `com.ensody.nativebuilds:nghttp2-libnghttp2`: static library
* **nghttp3** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/nghttp3-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/nghttp3-headers)
  * `com.ensody.nativebuilds:nghttp3-headers`: headers
  * `com.ensody.nativebuilds:nghttp3-libnghttp3`: static library
* **ngtcp2** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/ngtcp2-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/ngtcp2-headers)
  * `com.ensody.nativebuilds:ngtcp2-headers`: headers
  * `com.ensody.nativebuilds:ngtcp2-libngtcp2`: main static library
  * `com.ensody.nativebuilds:ngtcp2-libngtcp2_crypto_ossl`: OpenSSL helper library
* **brotli** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/brotli-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/brotli-headers)
  * `com.ensody.nativebuilds:brotli-headers`: headers
  * `com.ensody.nativebuilds:brotli-libbrotlicommon`: common static library
  * `com.ensody.nativebuilds:brotli-libbrotlidec`: decompression static library (depends on libbrotlicommon)
  * `com.ensody.nativebuilds:brotli-libbrotlienc`: compression static library (depends on libbrotlicommon)
* **lz4** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/lz4-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/lz4-headers)
  * `com.ensody.nativebuilds:lz4-headers`: headers
  * `com.ensody.nativebuilds:lz4-libzl4`: static library
* **zlib** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/zlib-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/zlib-headers)
  * `com.ensody.nativebuilds:zlib-headers`: headers
  * `com.ensody.nativebuilds:zlib-libz`: static library
* **zstd** [![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/zstd-headers?label=&color=%2345cf00&filter=!*-*)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/zstd-headers)
  * `com.ensody.nativebuilds:zstd-headers`: headers
  * `com.ensody.nativebuilds:zstd-libzstd`: static library

The artifacts are published to Maven Central, so they can be easily consumed by Gradle and Kotlin Multiplatform projects.
A Gradle plugin is also provided to simplify the integration of the header files.

Apart from being easier to integrate, this project helps you stay up to date.
The NativeBuilds automatically publish new versions to Maven Central.
Tools like Dependabot can notify you of version updates coming from NativeBuilds.
For security critical libraries like OpenSSL this automation is very helpful and important.

If you're a library author:
This project also allows consumers of your library to update the underlying native library without you having to publish a new version of your own library.
Each static library (e.g. OpenSSL) is packaged like any other Kotlin module, so the normal Gradle dependency resolution rules apply.
It's even possible to substitute the static library with a debug version.

Finally, this project allows sharing/reusing the same underlying static library (e.g. OpenSSL or libcurl) in different projects.
For example, a KMP crypto library could depend on libcrypto.a and Ktor could also depend on libcrypto.a without causing duplication or symbol conflicts.

## Usage

Add the dependencies, based on the Maven modules mentioned above, to your `gradle/libs.versions.toml`:

```toml
[versions]
# Note: these might not be the latest version numbers. Please check the version badges above.
openssl = "3.6.0.4"
nativebuilds = "0.7.0"

[libraries]
# KMP wrapper module for libcrypto.a
nativebuilds-openssl-libcrypto = { module = "com.ensody.nativebuilds:openssl-libcrypto", version.ref = "openssl" }
# KMP wrapper module for libssl.a
nativebuilds-openssl-libssl = { module = "com.ensody.nativebuilds:openssl-libssl", version.ref = "openssl" }
# Needed to integrate the OpenSSL headers for cinterop (only if you need to call the C API directly).
nativebuilds-openssl-headers = { module = "com.ensody.nativebuilds:openssl-headers", version.ref = "openssl" }

[plugins]
nativebuilds = { id = "com.ensody.nativebuilds", version.ref = "nativebuilds" }
```

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.nativebuilds)
}

kotlin {
    // List all desired targets
    iosArm64()
    iosSimulatorArm64()
    iosx64()
    tvosArm64()
    linuxArm64()
    linuxX64()
    mingwX64()

    // Add the KMP dependency containing the pre-built static library (the .a file)
    sourceSets.nativeMain.dependencies {
        api(libs.nativebuilds.openssl.libcrypto)
    }

    // If you need direct access to the libcrypto/OpenSSL API you have to activate cinterop
    // for the OpenSSL header files.
    cinterops(libs.nativebuilds.openssl.headers) {
        definitionFile.set(file("src/nativeMain/cinterop/openssl.def"))
    }
}
```

Create `src/nativeMain/cinterop/openssl.def`, but don't define staticLibraries. The native .a is already part of `api(libs.nativebuilds.openssl.libcrypto)` above.

```
package = my.package.openssl
headerFilter = openssl/*
headers = openssl/evp.h \
          openssl/kdf.h \
          openssl/err.h \
          openssl/encoder.h \
          openssl/decoder.h \
          openssl/ec.h
compilerOpts = -DOPENSSL_NO_DEPRECATED
```

Make sure your `gradle.properties` activates cinterop:

```
kotlin.mpp.enableCInteropCommonization=true
```

After the Gradle sync you can import the OpenSSL APIs within nativeMain (or iosMain etc.).

## JNI: JVM and Android

On the JVM and on Android you can integrate the pre-built shared libraries for all desktop and Android targets by adding this to your build.gradle.kts:

```kotlin
addJvmNativeBuilds(libs.nativebuilds.openssl.libcrypto)
```

You'll also have to implement JNI wrappers in C/C++ and Kotlin and integrate the C/C++ code with the build as described in the next subsections. You can use the [Kompressor](https://github.com/ensody/Kompressor) project as a reference for how to do that.

### JNI: Android

For Android you'll also need to integrate your CMakeLists.txt:

```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/CMakeLists.txt")
        }
    }
}
```

The CMakeLists.txt can utilize a pre-generated CMake helper:

```cmake
# Define a variable for the generated CMake helper's path
set(NATIVEBUILDS_CMAKE_DIR "${PROJECT_SOURCE_DIR}/../../build/nativebuilds-cmake")

# Include the CMake helper.
# This automatically includes the headers for openssl-libcrypto and makes the shared library accessible.
include("${NATIVEBUILDS_CMAKE_DIR}/openssl-libcrypto-android.cmake")

# Link against the shared library
target_link_libraries(myproject openssl-libcrypto)
```

### JNI: JVM

On the JVM you currently need to invest a little bit more effort for your JNI wrapper. One possibility is to use Zig to cross-compile for all desktop targets.

Again, take a look at the [Kompressor](https://github.com/ensody/Kompressor) project:

* `assembleZigJni` in [build.gradle.kts](https://github.com/ensody/Kompressor/blob/main/kompressor-zstd--nativelib/build.gradle.kts)
* [build.zig](https://github.com/ensody/Kompressor/blob/main/kompressor-zstd--nativelib/src/jvmMain/build.zig)

## Android unit tests

The NativeBuilds Gradle plugin provides a helper function for Android unit tests which substitutes all Android JNI dependencies with their JVM counterpart. Without that rule, Android unit tests with JNI dependencies would fail because the tests run directly on the host instead of an Android emulator - while the JNI dependencies are usually only built for Android only instead of for the host.

This substitution helper only changes the Android unit tests. The main Android source-set stays untouched. Add this to your build.gradle.kts or build-logic module:

```kotlin
substituteAndroidNativeLibsInUnitTests()
```

If you want to customize the rule, you can use the code in [AndroidSubstitutionRule.kt](https://github.com/ensody/native-builds/blob/main/nativebuilds-gradle-plugin/src/main/kotlin/com/ensody/nativebuilds/AndroidSubstitutionRule.kt) as a starting point.

## Local testing

```shell
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/29.0.13599879
export BUILD_TARGETS=macosArm64,macosX64,iosArm64,iosSimulatorArm64,iosX64,androidNativeArm64,androidNativeArm32
./gradlew assembleProjects
PUBLISHING=true ./gradlew generateBuildScripts
PUBLISHING=true WITH_WRAPPERS=true ./gradlew publishAllPublicationsToLocalMavenRepository
```

The artifacts will be placed in build/localmaven.

## License

```
Copyright 2025 Ensody GmbH, Waldemar Kornewald

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

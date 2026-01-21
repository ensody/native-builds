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

### Prerequisites

If you want to implement a JNI integration in C/C++ using the NativeBuilds Gradle plugin, you must install [Zig](https://ziglang.org) and place it in your PATH.
Zig is used for cross-compiling your C/C++ code to all targets (linuxArm64, linuxX64, macosArm64, macosX64, mingwX64).

You don't need Zig if you're just consuming the pre-built libraries (i.e., you're not writing any C/C++ code).

### Example

The `example` folder in this repo contains a minimal sample project which just calls `ZSTD_versionString()` from libzstd. It builds for several native targets and JVM and Android. You have to run `./gradlew assemble` once to build all necessary artifacts, so the IDE's Gradle sync won't warn about missing files.

Take a look at the [Kompressor](https://github.com/ensody/Kompressor) project to see a slightly more complex real-world example. NativeBuilds is used in the modules that are named "...--nativelib".

### Step by step

Add the dependencies, based on the Maven modules mentioned above, to your `gradle/libs.versions.toml`:

```toml
[versions]
# Note: these might not be the latest version numbers. Please check the version badges above.
openssl = "3.6.0.13"
nativebuilds = "0.8.0"

[libraries]
# KMP wrapper module for libcrypto.a
nativebuilds-openssl-libcrypto = { module = "com.ensody.nativebuilds:openssl-libcrypto", version.ref = "openssl" }
# KMP wrapper module for libssl.a
nativebuilds-openssl-libssl = { module = "com.ensody.nativebuilds:openssl-libssl", version.ref = "openssl" }
# Needed to integrate the OpenSSL headers for cinterop (only if you're creating a C/C++ wrapper
# and need to call the C API directly).
nativebuilds-openssl-headers = { module = "com.ensody.nativebuilds:openssl-headers", version.ref = "openssl" }

[plugins]
nativebuilds = { id = "com.ensody.nativebuilds", version.ref = "nativebuilds" }
```

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library") // or com.android.library
    alias(libs.plugins.nativebuilds)
}

kotlin {
    // List all desired targets. The NativeBuilds Gradle plugin will take the active targets into account.
    androidTarget()
    jvm()
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

// Add the KMP dependency for JNI C++ integration. This will integrate both Android and JVM.
jniNativeBuild(
    name = "libcrypto-jni",
    // This links against the libcrypto shared library and makes the headers available in your C/C++ code.
    nativeBuilds = listOf(
        libs.nativebuilds.openssl.headers,
        libs.nativebuilds.openssl.libcrypto,
    ),
) {
    // Path(s) to the JNI C++ source code (where the .cpp files reside)
    inputFiles.from("src/jvmCommonMain/jni")

    // Optional: Path(s) to any additional headers
    // includeDirs.from("../jni/common/include")
}

// This allows Android unit tests to run on the host
substituteAndroidNativeLibsInUnitTests()
```

For Kotlin/Native, create `src/nativeMain/cinterop/openssl.def`, but don't define `staticLibraries`.
The native .a is already part of `api(libs.nativebuilds.openssl.libcrypto)` in the `dependencies` block above.

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

After a Gradle sync you can import the OpenSSL APIs within nativeMain (or iosMain etc.).

Also, you can place JNI C++ code under `src/jvmCommonMain/jni` (as configured above).
You don't need to write any additional build scripts (not even the Android CMakeLists.txt).
On the JVM and Android, you can load the shared library at runtime like this:

```kotlin
NativeBuildsJvmLoader.load(NativeBuildsJvmLibCrypto)
```

Again, take a look at [Kompressor](https://github.com/ensody/Kompressor) for a real-world usage example.

## Android unit tests

The NativeBuilds Gradle plugin provides a helper function for Android unit tests which substitutes all Android JNI dependencies with their JVM counterpart. Without that rule, Android unit tests with JNI dependencies would fail because the tests run directly on the host instead of an Android emulator - while the JNI dependencies are usually only built for Android only instead of for the host.

This substitution helper only changes the Android unit tests. The main Android source-set stays untouched. Add this to your build.gradle.kts or build-logic module:

```kotlin
substituteAndroidNativeLibsInUnitTests()
```

If you want to customize the rule, you can use the code in [AndroidSubstitutionRule.kt](https://github.com/ensody/native-builds/blob/main/nativebuilds-gradle-plugin/src/main/kotlin/com/ensody/nativebuilds/AndroidSubstitutionRule.kt) as a starting point.

## Local testing

Note: Only not-yet-published versions get built. In `nativebuilds/build.gradle.kts` you can change the
`republishVersionSuffix` to re-map to a new version in order to force a new build.

```shell
export ANDROID_NDK_ROOT=$ANDROID_SDK_ROOT/ndk/29.0.13599879
export BUILD_TARGETS=macosArm64,macosX64,iosArm64,iosSimulatorArm64,iosX64,androidNativeArm64,androidNativeArm32
./gradlew assembleProjects
PUBLISHING=true ./gradlew generateBuildScripts
# This publishes to build/localmaven
PUBLISHING=true WITH_WRAPPERS=true ./gradlew publishAllPublicationsToLocalMavenRepository
# Alternatively, you can publish to mavenLocal
# PUBLISHING=true WITH_WRAPPERS=true ./gradlew pTML
```

## License

```
Copyright 2026 Ensody GmbH, Waldemar Kornewald

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

# Native builds of C/C++ libraries for Kotlin Multiplatform

[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/openssl?label=openssl&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/openssl)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/curl?label=curl&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/curl)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/nghttp2?label=nghttp2&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/nghttp2)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/nghttp3?label=nghttp3&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/nghttp3)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/ngtcp2?label=ngtcp2&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/ngtcp2)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/lz4?label=lz4&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/lz4)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/zlib?label=zlib&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/zlib)
[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/zstd?label=zstd&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/zstd)

[![Maven Central](https://img.shields.io/maven-central/v/com.ensody.nativebuilds/native-builds-gradle-plugin?label=native-builds-gradle-plugin&color=%2345cf00)](https://central.sonatype.com/artifact/com.ensody.nativebuilds/native-builds-gradle-plugin)

This project regularly builds the latest available vcpkg version of the following libraries:

* OpenSSL
  * `com.ensody.nativebuilds:openssl`: headers
  * `com.ensody.nativebuilds:openssl-libcrypto`: core crypto library
  * `com.ensody.nativebuilds:openssl-libssl`: TLS library (optional; depends on libcrypto)
* curl
  * `com.ensody.nativebuilds:curl`: headers and static library (depends on openssl, nghttp2, nghttp3, ngtcp2, zlib)
* nghttp2
  * `com.ensody.nativebuilds:nghttp2`: headers and static library
* nghttp3
  * `com.ensody.nativebuilds:nghttp3`: headers and static library
* ngtcp2
  * `com.ensody.nativebuilds:ngtcp2`: headers
  * `com.ensody.nativebuilds:ngtcp2-libngtcp2`: main static library
  * `com.ensody.nativebuilds:ngtcp2-libngtcp2_crypto_ossl`: OpenSSL helper library
* lz4
  * `com.ensody.nativebuilds:lz4`: headers and static library
* zlib
  * `com.ensody.nativebuilds:zlib`: headers and static library
* zstd
  * `com.ensody.nativebuilds:zstd`: headers and static library

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
openssl = "3.5.2.1"
zstd = "1.5.7.1"
nativebuilds = "0.3.0"

[libraries]
# KMP wrapper module for libcrypto.a
nativebuilds-openssl-libcrypto = { module = "com.ensody.nativebuilds:openssl-libcrypto", version.ref = "openssl" }
# KMP wrapper module for libssl.a
nativebuilds-openssl-libssl = { module = "com.ensody.nativebuilds:openssl-libssl", version.ref = "openssl" }
# Needed to integrate the OpenSSL headers for cinterop (only if you need to call the C API directly).
nativebuilds-openssl-headers = { module = "com.ensody.nativebuilds:openssl", version.ref = "openssl" }

# Unlike OpenSSL and ngtcp2, most libraries (like zstd) consist of only one binary, so a single dependency is sufficient
nativebuilds-zstd = { module = "com.ensody.nativebuilds:zstd", version.ref = "zstd" }

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
    // Note: for zstd you'd use libs.nativebuilds.zstd both here and in sourceSets.nativeMain.
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

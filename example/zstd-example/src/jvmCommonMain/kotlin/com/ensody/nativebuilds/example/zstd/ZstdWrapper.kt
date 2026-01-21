package com.ensody.nativebuilds.example.zstd

import com.ensody.nativebuilds.loader.NativeBuildsJvmLib
import com.ensody.nativebuilds.loader.NativeBuildsJvmLoader
import com.ensody.nativebuilds.zstd.NativeBuildsJvmLibZstd

internal object ZstdWrapper : NativeBuildsJvmLib {
    override val packageName: String = "zstd"
    override val libName: String = "zstd-jni"
    override val platformFileName: Map<String, String> = mapOf(
        "linuxArm64" to "libzstd-jni.so",
        "linuxX64" to "libzstd-jni.so",
        "macosArm64" to "libzstd-jni.dylib",
        "macosX64" to "libzstd-jni.dylib",
        "mingwX64" to "libzstd-jni.dll",
    )

    init {
        NativeBuildsJvmLoader.load(NativeBuildsJvmLibZstd)
        NativeBuildsJvmLoader.load(this)
    }

    external fun getZstdVersion(): String
}

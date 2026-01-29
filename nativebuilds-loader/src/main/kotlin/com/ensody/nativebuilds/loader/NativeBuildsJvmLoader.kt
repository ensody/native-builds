package com.ensody.nativebuilds.loader

import java.io.File
import kotlin.uuid.Uuid

/**
 * JNI native .so (shared library) loading mechanism for JVM and Android.
 *
 * This works with the NativeBuilds project out of the box and can also be customized via [overrideLoader].
 */
public object NativeBuildsJvmLoader {
    private val loadedLibs = mutableSetOf<NativeBuildsJvmLib>()
    private val tempDir by lazy {
        File.createTempFile(Uuid.random().toHexString(), null).apply {
            delete()
            mkdir()
            deleteOnExit()
        }
    }

    /**
     * Allows customizing the loading mechanism.
     *
     * The default value is the default loading mechanism. To delegate to the default loading mechanism you can save
     * the old value before overriding:
     *
     * ```kotlin
     * val original = NativeBuildsJvmLoader.overrideLoader
     * NativeBuildsJvmLoader.overrideLoader = {
     *     if (...) {
     *         original(it)
     *     } else {
     *         // custom logic
     *     }
     * }
     * ```
     */
    public var overrideLoader: (lib: NativeBuildsJvmLib) -> Unit = ::realLoad

    /**
     * Loads the given [lib] for use with JNI code.
     */
    public fun load(lib: NativeBuildsJvmLib) {
        synchronized(lib) {
            if (lib !in loadedLibs) {
                overrideLoader(lib)
                loadedLibs.add(lib)
            }
        }
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun realLoad(lib: NativeBuildsJvmLib) {
        val vendor = System.getProperty("java.vendor")
        if (vendor == "The Android Project") {
            System.loadLibrary(lib.libName.removePrefix("lib"))
            loadedLibs.add(lib)
            return
        }
        val osName = System.getProperty("os.name")!!.lowercase()
        val osArch = when (val osArch = System.getProperty("os.arch")!!.lowercase()) {
            "x86_64", "amd64" -> "X64"
            "aarch64", "arm64" -> "Arm64"
            else -> error("Unsupported arch: $osArch (os=$osName)")
        }
        val platform = when {
            "windows" in osName -> "mingw$osArch"
            "linux" in osName -> "linux$osArch"
            "mac" in osName || "darwin" in osName -> "macos$osArch"
            else -> error("Unsupported OS: $osName (arch=$osArch)")
        }
        val libFileName = lib.platformFileName[platform]
            ?: error("Could not find library ${lib.libName} for platform $platform")
        val path = "/jni/$platform/$libFileName"
        val tempFile = File(tempDir, libFileName).apply {
            deleteOnExit()
        }

        val resource = checkNotNull(lib::class.java.getResourceAsStream(path)) {
            "Could not find shared library: $path"
        }
        resource.use { inputStream ->
            tempFile.outputStream().use {
                inputStream.copyTo(it)
            }
        }

        System.load(tempFile.absolutePath)
    }
}

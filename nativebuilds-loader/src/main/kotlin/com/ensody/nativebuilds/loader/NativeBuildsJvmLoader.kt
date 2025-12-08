package com.ensody.nativebuilds.loader

import java.io.File

public object NativeBuildsJvmLoader {
    private val loadedLibs = mutableSetOf<NativeBuildsJvmLib>()

    public fun load(lib: NativeBuildsJvmLib) {
        synchronized(lib) {
            if (lib !in loadedLibs) {
                val vendor = System.getProperty("java.vendor")?.lowercase()
                if (vendor == "The Android Project") {
                    System.loadLibrary(lib.libName)
                    loadedLibs.add(lib)
                    return
                }
                val osName = System.getProperty("os.name").lowercase()
                val osArch = when (val osArch = System.getProperty("os.arch").lowercase()) {
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
                val tempFile = File.createTempFile(libFileName, null).apply {
                    deleteOnExit()
                }

                val resource = checkNotNull(lib::class.java.getResourceAsStream(path)) {
                    "Could not find shared library: $path"
                }
                resource.use { inputStream ->
                    tempFile.outputStream().use {
                        inputStream.transferTo(it)
                    }
                }

                System.load(tempFile.absolutePath)
                loadedLibs.add(lib)
            }
        }
    }
}

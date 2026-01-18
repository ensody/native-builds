package com.ensody.nativebuilds

import org.jetbrains.kotlin.konan.target.HostManager
import java.io.File

public fun locateExecutable(path: String): String =
    if ("/" in path || "\\" in path) {
        path
    } else {
        if (HostManager.hostIsMingw) {
            ProcessBuilder("cmd", "/c", "where $path")
        } else {
            ProcessBuilder(System.getenv("SHELL") ?: "/bin/sh", "-l", "-c", "command -v $path")
        }.start().inputStream.bufferedReader().readText().trim().lines().firstOrNull()
            ?: error("Could not resolve zig path: $path")
    }

public fun String.quoted(): String =
    "\"${
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\t", "\\\t")
            .replace("\r", "\\\r")
            .replace("\n", "\\\n")
    }\""

public fun File.writeTextIfDifferent(text: String) {
    if (!exists() || readText() != text) {
        parentFile.mkdirs()
        writeText(text)
    }
}

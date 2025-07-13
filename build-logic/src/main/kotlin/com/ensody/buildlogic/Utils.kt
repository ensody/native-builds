package com.ensody.buildlogic

import java.io.File

fun cli(
    vararg command: String,
    workingDir: File? = null,
    env: Map<String, String> = emptyMap(),
    inheritIO: Boolean = false,
): String {
    var cmd = command.first().replace("/", File.separator)
    if (OS.current == OS.Windows) {
        if (File("$cmd.bat").exists()) {
            cmd += ".bat"
        } else if (File("$cmd.exe").exists()) {
            cmd += ".exe"
        }
    }
    val processBuilder = ProcessBuilder(cmd, *command.drop(1).toTypedArray())
    workingDir?.let { processBuilder.directory(it) }
    processBuilder.redirectErrorStream(true)
    processBuilder.environment().putAll(env)
    val process = processBuilder.start()
    return process.inputStream.bufferedReader().readText().trim().also {
        val exitCode = process.waitFor()
        if (inheritIO) {
            println(it)
        }
        check(exitCode == 0) { "Process exit code was: $exitCode\nOriginal command: ${command.toList()}\nResult:$it" }
    }
}

fun shell(
    command: String,
    workingDir: File? = null,
    env: Map<String, String> = emptyMap(),
    inheritIO: Boolean = false,
): String =
    cli("/bin/bash", "-c", command, workingDir = workingDir, env = env, inheritIO = inheritIO)

enum class OS {
    Linux,
    macOS,
    Windows,
    ;

    companion object Companion {
        val current: OS by lazy {
            val osName = System.getProperty("os.name").lowercase()
            when {
                "mac" in osName || "darwin" in osName -> macOS
                "linux" in osName -> Linux
                "windows" in osName -> Windows
                else -> error("Unknown operating system: $osName")
            }
        }
    }
}

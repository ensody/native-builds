package com.ensody.buildlogic

import java.io.File

fun cli(
    vararg command: String,
    workingDir: File? = null,
    env: Map<String, String> = emptyMap(),
    inheritIO: Boolean = false,
): String {
    var cmd = command.first().replace("/", File.separator)
    if ("windows" in System.getProperty("os.name").lowercase()) {
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
        check(exitCode == 0) { "Process exit code was: $exitCode\nOriginal command: $command" }
    }
}

fun shell(
    command: String,
    workingDir: File? = null,
    env: Map<String, String> = emptyMap(),
    inheritIO: Boolean = false,
): String =
    cli("/bin/bash", "-c", command, workingDir = workingDir, env = env, inheritIO = inheritIO)

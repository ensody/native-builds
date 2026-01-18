package com.ensody.nativebuilds

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

public abstract class CopyJniHeaders : DefaultTask() {

    @get:OutputDirectory
    public abstract val jniHeadersOutputDirectory: DirectoryProperty

    init {
        group = "build"
        jniHeadersOutputDirectory.convention(project.layout.buildDirectory.dir("nativebuilds-jni/${name}"))
    }

    @TaskAction
    public fun run() {
        val jniFiles = listOf(
            "jni/include/share/jni.h",
            "jni/include/unix/jni_md.h",
            "jni/include/windows/jni_md.h",
            "jni/include/LICENSE",
        )
        for (path in jniFiles) {
            val outputPath = jniHeadersOutputDirectory.file(path).get().asFile
            outputPath.parentFile.mkdirs()
            outputPath.writeTextIfDifferent(
                CopyJniHeaders::class.java.module.getResourceAsStream(path)!!.readAllBytes().decodeToString(),
            )
        }
    }
}

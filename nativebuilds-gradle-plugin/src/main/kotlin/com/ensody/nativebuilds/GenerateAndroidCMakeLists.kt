package com.ensody.nativebuilds

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

public abstract class GenerateAndroidCMakeLists : DefaultTask(), JniBuildTask {
    @get:Input
    public abstract val nativeBuilds: ListProperty<Provider<MinimalExternalModuleDependency>>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @Internal
    override val jniTarget: JniTarget = JniTarget.Android

    init {
        group = "build"
    }

    @TaskAction
    public fun run() {
        val content = buildString {
            appendLine("cmake_minimum_required(VERSION 3.4.1)")
            appendLine("project(${outputLibraryName.get()})")
            val artifacts = nativeBuilds.get().map { it.get() }
            for (artifact in artifacts) {
                val helperPath = project.file("build/nativebuilds-cmake/${artifact.module.name}-android.cmake")
                appendLine("include(${helperPath.absolutePath})")
            }

            appendLine("add_library(${outputLibraryName.get()} SHARED ${inputFiles.asFileTree.joinToString(" ") { it.absolutePath.quoted() }})")
            appendLine("include_directories(${outputLibraryName.get()} ${includeDirs.joinToString(" ") { it.absolutePath.quoted() }})")
            appendLine("target_link_libraries(${outputLibraryName.get()} ${artifacts.joinToString(" ") { it.module.name }})")
        }
        outputDirectory.file("CMakeLists.txt").get().asFile.writeTextIfDifferent(content)
    }
}

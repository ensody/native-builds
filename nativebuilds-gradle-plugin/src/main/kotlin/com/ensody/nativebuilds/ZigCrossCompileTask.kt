package com.ensody.nativebuilds

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import javax.inject.Inject

public abstract class ZigCrossCompileTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask(), CommonBuildTask {

    @get:Input
    public abstract val target: Property<String>

    @get:InputFiles
    public abstract val linkPaths: ConfigurableFileCollection

    @get:Input
    public abstract val linkLibraries: ListProperty<String>

    @get:Input
    public abstract val extraOpts: ListProperty<String>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @get:Input
    public abstract val zigCommand: ListProperty<String>

    @get:Input
    public abstract val zigSubcommand: ListProperty<String>

    init {
        group = "build"
        zigCommand.convention(listOf("zig"))
        zigSubcommand.convention(listOf("c++"))
    }

    protected open fun ExecSpec.addExtraConfigs() {
    }

    @TaskAction
    public open fun run() {
        outputDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }

        execOperations.exec {
            workingDir(outputDirectory.get())

            commandLine(
                locateExecutable(zigCommand.get().firstOrNull() ?: "zig"),
                *zigCommand.get().drop(1).toTypedArray(),
                *zigSubcommand.get().toTypedArray(),
                "-target",
                target.get(),
            )

            addExtraConfigs()

            args(extraOpts.get())
            args(includeDirs.map { "-I${it.absolutePath}" })
            args(linkPaths.map { "-L${it.absolutePath}" })
            args(linkLibraries.get().map { "-l$it" })

            inputFiles.asFileTree.forEach {
                if (it.extension in allowedExtensions) {
                    args(it.absolutePath)
                }
            }
        }
    }
}

private val allowedExtensions = listOf("c", "cpp")

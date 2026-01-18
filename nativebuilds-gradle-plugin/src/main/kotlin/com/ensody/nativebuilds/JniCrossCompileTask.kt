package com.ensody.nativebuilds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File
import javax.inject.Inject

public abstract class CompileJni @Inject constructor(
    @get:Input override val jniTarget: JniTarget.Desktop,
    private val execOperations: ExecOperations,
) : ZigCrossCompileTask(execOperations), JniBuildTask {

    @Internal
    public val targetName: String = jniTarget.konanTarget.getSourceSetName()

    @get:Input
    public abstract val nativeBuilds: ListProperty<Provider<MinimalExternalModuleDependency>>

    init {
        target.set(jniTarget.zigTargetName)
    }

    override fun ExecSpec.addExtraConfigs() {
        val fullLibraryName = outputLibraryName.get() + "." + jniTarget.konanTarget.family.dynamicSuffix
        args(
            "-shared",
            "-Os",
            "-fPIC",
            "-o",
            fullLibraryName,
        )

        for (artifact in nativeBuilds.get()) {
            val rawArtifact = artifact.get()
            val basePath = project.file("build/nativebuilds/${rawArtifact.module.name}-jvm")
            val linkPath = File(basePath, "jni/${targetName}")
            args("-L${linkPath.absolutePath}")

            val config = Json.decodeFromString<JsonObject>(
                File(basePath, "META-INF/nativebuild.json").readText(),
            )
            val pkg = config.getValue("package").jsonPrimitive.content
            val lib = config.getValue("lib").jsonPrimitive.content
            val platformFileName = config.getValue("platformFileName").jsonObject.mapValues {
                it.value.jsonPrimitive.content
            }

            val fileName = platformFileName.getValue(jniTarget.konanTarget.getSourceSetName()).substringBeforeLast(".")
            val libName = if (jniTarget is JniTarget.Desktop.Windows) fileName else lib.removePrefix("lib")
            args("-l$libName")

            val baseHeadersPath = project.file("build/nativebuilds/$pkg-headers")
            val headersPaths = listOf(
                File(baseHeadersPath, "common"),
                File(baseHeadersPath, targetName),
            )
            for (headersPath in headersPaths) {
                if ("-I$headersPath" !in args) {
                    args("-I$headersPath")
                }
            }
        }

    }

    @TaskAction
    override fun run() {
        super.run()

        // Delete the DefaultLoad.lib or whatever else gets generated unnecessarily
        outputDirectory.get().asFile.listFiles().forEach {
            if (it.extension !in listOf("so", "dylib", "dll")) it.delete()
        }
    }
}

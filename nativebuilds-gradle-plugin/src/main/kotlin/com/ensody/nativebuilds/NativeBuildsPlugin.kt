@file:Suppress("UnstableApiUsage")

package com.ensody.nativebuilds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.support.unzipTo
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import java.io.File
import java.util.zip.ZipFile

public class NativeBuildsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.run {
            val nativeBuild = configurations.create("nativeBuild")
            tasks.register("unzipNativeBuilds") {
                inputs.files(nativeBuild)
                outputs.dir(layout.buildDirectory.dir("nativebuilds"))
                val artifactsProvider = nativeBuild.incoming.artifactView {
                    lenient(true)
                }.artifacts

                doLast {
                    outputs.files.singleFile.deleteRecursively()
                    artifactsProvider.artifacts.forEach { artifact ->
                        val identifier = artifact.id as ModuleComponentArtifactIdentifier
                        val name = identifier.componentIdentifier.moduleIdentifier.name
                        val outputDir = File(outputs.files.singleFile, name)
                        unzipTo(outputDir, artifact.file)
                        val classesJar = File(outputDir, "classes.jar")
                        if (artifact.file.extension == "aar" && classesJar.exists()) {
                            val metadataPath = "META-INF/nativebuild.json"
                            ZipFile(classesJar).useToRun {
                                getEntry(metadataPath)?.let { getInputStream(it).readBytes() }
                            }?.also {
                                File(outputDir, metadataPath).writeBytes(it)
                            }
                        }
                    }
                }
            }
            tasks.withType<CInteropProcess> {
                dependsOn("unzipNativeBuilds")
            }
            tasks.withType<Jar> {
                dependsOn("unzipNativeBuilds")
            }
            // Android
            tasks.findByName("preBuild")?.apply {
                dependsOn("unzipNativeBuilds")
            }
        }
    }
}

@file:Suppress("UnstableApiUsage")

package com.ensody.nativebuilds

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.kotlin.dsl.support.unzipTo
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

class NativeBuildsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.run {
            tasks.withType<CInteropProcess> {
                dependsOn("unzipNativeBuilds")
            }
            val nativeBuild = configurations.create("nativeBuild")
            tasks.register("unzipNativeBuilds") {
                inputs.files(nativeBuild)
                outputs.dir(layout.buildDirectory.dir("nativebuilds"))

                doLast {
                    nativeBuild.incoming.artifacts.forEach { artifact ->
                        val identifier = artifact.id as ModuleComponentArtifactIdentifier
                        val name = identifier.componentIdentifier.moduleIdentifier.name
                        val outputDir = project.layout.buildDirectory.dir("nativebuilds/$name").get().asFile
                        unzipTo(outputDir, artifact.file)
                    }
                }
            }
            tasks.withType<CInteropProcess> {
                dependsOn("unzipNativeBuilds")
            }
        }
    }
}

fun KotlinMultiplatformExtension.cinterops(
    vararg artifacts: Provider<MinimalExternalModuleDependency>,
    includeHeadersPath: Boolean = true,
    includeLibraryPath: Boolean = false,
    block: DefaultCInteropSettings.() -> Unit,
) {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops(
                artifacts = artifacts,
                includeHeadersPath = includeHeadersPath,
                includeLibraryPath = includeLibraryPath,
                block = block,
            )
        }
    }
}

fun KotlinNativeCompilation.cinterops(
    vararg artifacts: Provider<MinimalExternalModuleDependency>,
    includeHeadersPath: Boolean = true,
    includeLibraryPath: Boolean = false,
    block: DefaultCInteropSettings.() -> Unit,
) {
    val name = artifacts.first().get().name
    for (artifact in artifacts) {
        val rawArtifact = artifact.get()
        val dependency = project.dependencies.create(
            "${rawArtifact.module.group}:${rawArtifact.module.name}-${target.name.lowercase()}:${rawArtifact.version}",
        ) as ExternalModuleDependency
        dependency.artifact {
            extension = "zip"
            type = "zip"
        }
        project.dependencies.add("nativeBuild", dependency)
    }
    cinterops {
        create(name) {
            val basePath = "nativebuilds/$name-${target.name}"
            if (includeHeadersPath) {
                includeDirs(project.layout.buildDirectory.dir("$basePath/include"))
            }
            if (includeLibraryPath) {
                extraOpts("-libraryPath", project.layout.buildDirectory.dir("$basePath/lib").get().asFile.path)
            }
            block()
        }
    }
}

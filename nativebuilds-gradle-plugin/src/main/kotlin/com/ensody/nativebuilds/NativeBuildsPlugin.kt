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

public fun KotlinMultiplatformExtension.cinterops(
    vararg artifacts: Provider<MinimalExternalModuleDependency>,
    includeHeadersPath: Boolean = true,
    block: DefaultCInteropSettings.() -> Unit,
) {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops(
                artifacts = artifacts,
                includeHeadersPath = includeHeadersPath,
                block = block,
            )
        }
    }
}

public fun KotlinNativeCompilation.cinterops(
    vararg artifacts: Provider<MinimalExternalModuleDependency>,
    includeHeadersPath: Boolean = true,
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
            val basePath = "nativebuilds/$name-${target.name.lowercase()}"
            if (includeHeadersPath) {
                includeDirs(project.layout.buildDirectory.dir("$basePath/include"))
            }
            block()
        }
    }
}

public fun Project.addJvmNativeBuilds(
    vararg artifacts: Provider<MinimalExternalModuleDependency>,
    targets: List<JvmNativeBuildTarget> = listOf(JvmNativeBuildTarget.Android, JvmNativeBuildTarget.Jvm),
) {
    for (artifact in artifacts) {
        val rawArtifact = artifact.get()
        for (target in targets) {
            dependencies.add(
                "nativeBuild",
                "${rawArtifact.module.group}:${rawArtifact.module.name}-${target.suffix}:${rawArtifact.version}",
            ) {
                isTransitive = false
            }

            tasks.named("unzipNativeBuilds") {
                doLast {
                    val nativeBuildsPath = file("build/nativebuilds")
                    val metadata = Json.decodeFromString<JsonObject>(
                        File(
                            nativeBuildsPath,
                            "${rawArtifact.module.name}-${target.suffix}/META-INF/nativebuild.json",
                        ).readText(),
                    )
                    val pkg = metadata.getValue("package").jsonPrimitive.content
                    val lib = metadata.getValue("lib").jsonPrimitive.content
                    val cmakeHeader = """
                    project(${rawArtifact.module.name})
                    set(NATIVEBUILDS_DIR ${nativeBuildsPath.absolutePath.quoted()})
                    add_library(${rawArtifact.module.name} SHARED IMPORTED)
                    """.trimIndent()
                    val cmakeRule = when (target) {
                        JvmNativeBuildTarget.Android -> {
                            """
                            set_target_properties(${rawArtifact.module.name} PROPERTIES IMPORTED_LOCATION
                                "${'$'}{NATIVEBUILDS_DIR}/$pkg-$lib-${target.suffix}/jni/${'$'}{CMAKE_ANDROID_ARCH_ABI}/$lib.so"
                            )
                            include_directories("${'$'}{NATIVEBUILDS_DIR}/$pkg-headers-androidnativearm64/include")
                            """.trimIndent() + "\n"
                        }

                        // TODO: On JVM we should probably generate a Zig-based build file instead, so we can
                        //  cross-compile more easily.
                        JvmNativeBuildTarget.Jvm -> {
                            """
                            if (CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64" OR CMAKE_SYSTEM_PROCESSOR MATCHES "AMD64")
                              set(KMP_ARCH "X64")
                            elseif (CMAKE_SYSTEM_PROCESSOR MATCHES "arm64" OR CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
                              set(KMP_ARCH "Arm64")
                            else ()
                              message(FATAL_ERROR "Unsupported target architecture: ${'$'}{CMAKE_SYSTEM_PROCESSOR}")
                            endif ()

                            if (CMAKE_SYSTEM_NAME STREQUAL "Linux")
                              set(KMP_OS "linux")
                              set(KMP_LIB_EXT "so")
                            elseif (CMAKE_SYSTEM_NAME STREQUAL "Windows")
                              set(KMP_OS "mingw")
                              set(KMP_LIB_EXT "dll")
                            elseif (CMAKE_SYSTEM_NAME STREQUAL "Darwin")
                              set(KMP_OS "macos")
                              set(KMP_LIB_EXT "dylib")
                            else ()
                              message(FATAL_ERROR "Unsupported target OS: ${'$'}{CMAKE_SYSTEM_NAME}")
                            endif ()

                            set(KMP_QUALIFIER "${'$'}{KMP_OS}${'$'}{KMP_ARCH}")
                            string(TOLOWER "${'$'}{KMP_QUALIFIER}" KMP_QUALIFIER_LOWER)

                            set_target_properties(${rawArtifact.module.name} PROPERTIES IMPORTED_LOCATION
                                "${'$'}{NATIVEBUILDS_DIR}/$pkg-$lib-${target.suffix}/jni/${'$'}{KMP_OS}${'$'}{KMP_ARCH}/$lib.${'$'}{KMP_LIB_EXT}"
                            )
                            include_directories("${'$'}{NATIVEBUILDS_DIR}/$pkg-headers-${'$'}{KMP_QUALIFIER_LOWER}/include")
                            """.trimIndent() + "\n"
                        }
                    }
                    file("build/nativebuilds-cmake/${rawArtifact.module.name}-${target.suffix}.cmake").apply {
                        parentFile.mkdirs()
                        if (!exists() || readText() != cmakeRule) {
                            writeText(cmakeHeader + "\n" + cmakeRule)
                        }
                    }
                }
            }
        }
    }
}

public enum class JvmNativeBuildTarget(public val suffix: String) {
    Android("android"),
    Jvm("jvm"),
}

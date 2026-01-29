package com.ensody.nativebuilds

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File

/**
 * Configures cross-compilation for a JNI shared library that links against the given [nativeBuilds].
 */
public fun Project.jniNativeBuild(
    name: String,
    nativeBuilds: List<Provider<MinimalExternalModuleDependency>>,
    targets: List<JvmNativeBuildTarget>? = null,
    block: JniBuildTask.() -> Unit,
) {
    val targets = targets ?: getDefaultJvmNativeBuildTargets()
    val libs = nativeBuilds.filter { !it.get().module.name.endsWith("-headers") }
    addJvmNativeBuilds(nativeBuilds = nativeBuilds.toTypedArray(), targets = targets)
    val androidExtension = extensions.findByType<LibraryExtension>() ?: extensions.findByType<ApplicationExtension>()
    val androidTestSourceSet = androidExtension?.sourceSets?.named("test")

    val copyJniHeadersTask = getJniHeadersTask()
    val mainTask = tasks.register("assemble$name") {
        dependsOn(copyJniHeadersTask)
    }.get()
    if (JvmNativeBuildTarget.Jvm in targets) {
        addJniDesktopBuildTasks(name) {
            dependsOn("unzipNativeBuilds")
            outputLibraryName.set("lib$name")
            this.nativeBuilds.addAll(libs)
            block()
            // Android unit tests run on the host, so integrate the native shared libs for the host system
            androidTestSourceSet?.configure { resources.srcDir(outputDirectory.get().asFile.parentFile.parentFile) }
        }.also { mainTask.dependsOn(it) }
    }

    if (JvmNativeBuildTarget.Android in targets) {
        checkNotNull(androidExtension) { "Could not find the Android Gradle Plugin" }

        val generateTask = tasks.register<GenerateAndroidCMakeLists>("generateCMakeLists") {
            group = "build"
            dependsOn("unzipNativeBuilds")
            outputLibraryName.set(name)
            this.nativeBuilds.addAll(libs)
            outputDirectory.set(file("build/nativebuilds-android/$name"))
            block()
        }

        tasks.named("prepareKotlinIdeaImport") {
            dependsOn(generateTask)
        }

        mainTask.dependsOn(generateTask)

        androidExtension.apply {
            externalNativeBuild {
                cmake {
                    path = generateTask.get().outputDirectory.file("CMakeLists.txt").get().asFile
                }
            }
        }

        // Needed for Android unit tests to access the native shared libs for the host system
        tasks.named("preBuild") {
            dependsOn(mainTask)
        }
    }
}

/**
 * You should normally use [jniNativeBuild]. This only adds the headers and dependencies and Android CMake rules.
 */
public fun Project.addJvmNativeBuilds(
    vararg nativeBuilds: Provider<MinimalExternalModuleDependency>,
    targets: List<JvmNativeBuildTarget>? = null,
) {
    addNativeBuildsHeaders(nativeBuilds = nativeBuilds)
    val libs = nativeBuilds.filter { !it.get().module.name.endsWith("-headers") }
    val targets = targets ?: getDefaultJvmNativeBuildTargets()
    for (artifact in libs) {
        val rawArtifact = artifact.get()
        for (target in targets) {
            dependencies.add(
                "nativeBuild",
                "${rawArtifact.module.group}:${rawArtifact.module.name}-${target.suffix}:${rawArtifact.version}",
            ) {
                isTransitive = false
            }

            if (target != JvmNativeBuildTarget.Android) {
                continue
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
                    add_library(${rawArtifact.module.name} INTERFACE)
                    """.trimIndent()
                    val cmakeRule =
                        """
                        target_link_directories(${rawArtifact.module.name} INTERFACE
                            "${'$'}{NATIVEBUILDS_DIR}/$pkg-$lib-${target.suffix}/jni/${'$'}{CMAKE_ANDROID_ARCH_ABI}"
                        )
                        target_link_libraries(${rawArtifact.module.name} INTERFACE $lib.so)
                        include_directories("${'$'}{NATIVEBUILDS_DIR}/$pkg-headers/common")
                        include_directories("${'$'}{NATIVEBUILDS_DIR}/$pkg-headers/androidNativeArm64")
                        """.trimIndent() + "\n"
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

internal fun Project.addJniDesktopBuildTasks(name: String, block: CompileJni.() -> Unit): Task {
    val copyJniHeadersTask = getJniHeadersTask()
    val kmpExtension = getKmpExtension()
    val desktopTargets = if (kmpExtension.hasTarget(KotlinPlatformType.jvm)) {
        JniTarget.Desktop.entries
    } else {
        emptyList()
    }
    val mainTask = tasks.register("assemble${name}Desktop").get()
    tasks.named("jvmProcessResources").configure { dependsOn(mainTask) }
    for (target in desktopTargets) {
        val task = tasks.register<CompileJni>(
            "assemble${name}${target.konanTarget.getSourceSetName().replaceFirstChar { it.uppercase() }}",
            target,
        )
        mainTask.dependsOn(task)
        task.configure {
            group = "build"
            dependsOn(copyJniHeadersTask)

            val hostDirName = when (target) {
                is JniTarget.Desktop.Linux, is JniTarget.Desktop.MacOS -> "unix"
                is JniTarget.Desktop.Windows -> "windows"
            }
            for (dir in listOf("share", hostDirName)) {
                includeDirs.from(copyJniHeadersTask.jniHeadersOutputDirectory.file("jni/include/$dir"))
            }

            outputDirectory.set(file("build/nativebuilds-desktop/$name/jni/${target.konanTarget.getSourceSetName()}"))

            block()
            kmpExtension.sourceSets.named("jvmMain").configure {
                resources.srcDir(outputDirectory.get().asFile.parentFile.parentFile)
            }
        }
    }
    return mainTask
}

internal fun Project.getDefaultJvmNativeBuildTargets(): List<JvmNativeBuildTarget> {
    val kmpExtension = getKmpExtension()
    return buildList {
        if (kmpExtension.hasTarget(KotlinPlatformType.androidJvm)) {
            add(JvmNativeBuildTarget.Android)
        }
        if (kmpExtension.hasTarget(KotlinPlatformType.jvm)) {
            add(JvmNativeBuildTarget.Jvm)
        }
    }
}

internal fun KotlinMultiplatformExtension.hasTarget(platformType: KotlinPlatformType): Boolean =
    targets.any { it.platformType == platformType }

internal fun Project.getKmpExtension(): KotlinMultiplatformExtension =
    checkNotNull(extensions.getByType<KotlinMultiplatformExtension>()) {
        "You have to add the Kotlin Multiplatform Kotlin Gradle Plugin in order to build JNI artifacts"
    }

internal fun Project.getJniHeadersTask(): CopyJniHeaders =
    tasks.withType<CopyJniHeaders>().firstOrNull()
        ?: tasks.register<CopyJniHeaders>("jniHeaders").get()

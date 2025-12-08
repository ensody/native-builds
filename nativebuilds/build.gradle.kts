import android.databinding.tool.ext.toCamelCase
import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.ensody.buildlogic.BuildPackage
import com.ensody.buildlogic.BuildTarget
import com.ensody.buildlogic.GroupId
import com.ensody.buildlogic.OS
import com.ensody.buildlogic.cli
import com.ensody.buildlogic.generateBuildGradle
import com.ensody.buildlogic.isOnArm64
import com.ensody.buildlogic.json
import com.ensody.buildlogic.loadBuildPackages
import com.ensody.buildlogic.registerZipTask
import com.ensody.buildlogic.renameLeafName
import com.ensody.buildlogic.setupBuildLogic
import io.ktor.http.quote
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

plugins {
    id("com.ensody.build-logic.base")
    id("com.ensody.build-logic.publish")
}

setupBuildLogic {}

// TODO: Debug builds will have to be done via overlays. They're not fully supported yet.
val includeDebugBuilds = System.getenv("INCLUDE_DEBUG_BUILDS") == "true"
val isPublishing = System.getenv("PUBLISHING") == "true"

val nativeBuildPath = layout.buildDirectory.dir("nativebuilds").get().asFile
val overlayTriplets = layout.buildDirectory.dir("nativebuilds-triplets").get().asFile
val wrappersPath = File("$rootDir/generated-kotlin-wrappers")
val initBuildTask = tasks.register("cleanNativeBuild") {
    doFirst {
        nativeBuildPath.deleteRecursively()
        if (!isPublishing) {
            wrappersPath.deleteRecursively()
            wrappersPath.mkdirs()
            File(
                wrappersPath,
                "pkg-${OS.current.name}-$splitId.json",
            ).writeTextIfDifferent(json.encodeToString(packages))
        }

        // If we don't delete this, vcpkg will think that the package might already be installed and skip the output
        // to x-packages-root.
        File("$rootDir/vcpkg_installed").deleteRecursively()

        overlayTriplets.deleteRecursively()
        overlayTriplets.mkdirs()
        val communityTriplets = File("$rootDir/vcpkg/triplets/community")
        val baseTriplets = (communityTriplets.listFiles()!! + communityTriplets.parentFile.listFiles()!!).filter {
            it.isFile && it.extension == "cmake"
        }
        for (target in BuildTarget.values()) {
            val file = baseTriplets.first { it.nameWithoutExtension == target.triplet }
            val destination = File(overlayTriplets, file.name)
            file.copyTo(destination)
            destination.appendText("\nset(VCPKG_BUILD_TYPE release)\n")

            if (target.dynamicLib) {
                val libFile = baseTriplets.first { it.nameWithoutExtension == target.baseDynamicTriplet }
                val dynamic = File(overlayTriplets, "${target.dynamicTriplet}.cmake")
                libFile.copyTo(dynamic)
                dynamic.appendText("\nset(VCPKG_CRT_LINKAGE dynamic)\nset(VCPKG_LIBRARY_LINKAGE dynamic)\n")
            }
        }
    }
}

// This is used to publish a new version in case the build script has changed fundamentally
val rebuildVersionWithSuffix = mapOf<String, Map<String, String>>(
    "curl" to mapOf("8.17.0" to ".4"),
    "lz4" to mapOf("1.10.0" to ".4"),
    "nghttp2" to mapOf("1.68.0" to ".4"),
    "nghttp3" to mapOf("1.13.1" to ".4"),
    "ngtcp2" to mapOf("1.18.0" to ".4"),
    "openssl" to mapOf("3.6.0" to ".4"),
    "zlib" to mapOf("1.3.1" to ".4"),
    "zstd" to mapOf("1.5.7" to ".4"),
)

val packages = loadBuildPackages(rootDir).map { pkg ->
    rebuildVersionWithSuffix[pkg.name]?.get(pkg.version)?.let { pkg.copy(version = pkg.version + it) } ?: pkg
}
println(packages.joinToString("\n") { "$it" })

val splits = System.getenv("MAX_SPLITS")?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
val splitId = System.getenv("BUILD_SPLIT_ID")?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
val targets = System.getenv("BUILD_TARGETS")?.takeIf { it.isNotBlank() }?.split(",")?.map {
    BuildTarget.valueOf(it)
}?.distinct()
    ?: when (OS.current) {
        OS.macOS -> listOf(
            BuildTarget.iosArm64,
            BuildTarget.iosSimulatorArm64,
            BuildTarget.iosX64,

            BuildTarget.tvosArm64,
            BuildTarget.tvosSimulatorArm64,
            BuildTarget.tvosX64,

            BuildTarget.watchosArm32,
            BuildTarget.watchosDeviceArm64,
            BuildTarget.watchosArm64,
            BuildTarget.watchosSimulatorArm64,
            BuildTarget.watchosX64,

            BuildTarget.macosArm64,
            BuildTarget.macosX64,
        )

        OS.Linux -> if (isOnArm64) {
            listOf(
                BuildTarget.linuxArm64,
            )
        } else {
            listOf(
                BuildTarget.linuxX64,

                BuildTarget.androidNativeArm64,
                BuildTarget.androidNativeArm32,
                BuildTarget.androidNativeX64,
                BuildTarget.androidNativeX86,
            )
        }

        OS.Windows -> listOf(
            BuildTarget.mingwX64,
//            BuildTarget.windowsX64,
        )
    }.run {
        val chunkSize = size / splits
        slice(chunkSize * splitId until if (splitId >= splits - 1) size else chunkSize * (splitId + 1))
    }

println("Building for targets: ${targets.joinToString(", ") { it.name }}")

val assembleTask = tasks.register("assembleProjects").get()
for (target in targets) {
    if (packages.all { it.isPublished }) continue

    val assemble = tasks.register("assemble-${target.name}") {
        group = "build"
        dependsOn(initBuildTask)
        doLast {
            for (dynamic in listOf(false, target.dynamicLib).distinct()) {
                val baseNativeBuildPath = File(nativeBuildPath, if (dynamic) "dynamic" else "static")
                val baseWrappersPath = File(wrappersPath, if (dynamic) "dynamic" else "static")
                val triplet = if (dynamic) target.dynamicTriplet else target.triplet
                cli(
                    "./vcpkg/vcpkg",
                    "install",
                    "--overlay-triplets=$overlayTriplets",
                    "--triplet",
                    triplet,
                    "--x-packages-root",
                    "$baseNativeBuildPath/${target.name}",
                    inheritIO = true,
                )
                for (pkg in packages) {
                    if (pkg.isPublished) continue

                    val sourceDir = File(baseNativeBuildPath, "${target.name}/${pkg.name}_$triplet")
                    val libs = File(sourceDir, "lib").listFiles().orEmpty()
                    val symlinks = libs.filter { it.canonicalPath != it.absolutePath }.groupBy { it.canonicalFile }
                    for ((canonical, linkedFrom) in symlinks) {
                        val bestLink = linkedFrom.minBy { it.name.length }
                        bestLink.delete()
                        canonical.renameTo(bestLink)
                        (linkedFrom - bestLink).forEach { it.delete() }
                    }

                    val destPath = File(baseWrappersPath, "${pkg.name}/libs/${target.name}")
                    copy {
                        from(sourceDir) {
                            if (dynamic) {
                                include("bin/**.dll")
                            }
                            include("lib/**")
                            if (!dynamic) {
                                include("include/**")
                            }
                            if (includeDebugBuilds) {
                                include("debug/**")
                            }
                            exclude("debug/lib/pkgconfig", "lib/pkgconfig")
                        }
                        into(destPath)
                    }
                    val binFolder = File(destPath, "bin")
                    binFolder.listFiles().orEmpty().toList().forEach {
                        if (it.extension == "dll") {
                            it.renameTo(File(destPath, "lib/${it.name}"))
                        }
                    }
                    if (binFolder.listFiles().isNullOrEmpty()) {
                        binFolder.deleteRecursively()
                    }
                    File(destPath, "lib/libzlib.a").renameLeafName("libz.a")
                    File(destPath, "lib/libzlib.so").renameLeafName("libz.so")
                }
            }
        }
    }
    assembleTask.dependsOn(assemble)
}

if (isPublishing) {
    wrappersPath.listFiles().orEmpty().filter { it.name.startsWith("pkg-") && it.extension == "json" }.flatMap {
        json.decodeFromString<List<BuildPackage>>(it.readText()).map { it.name to it.version }
    }.groupBy({ it.first }, { it.second }).forEach { (lib, versions) ->
        check(versions.toSet().size == 1) {
            "Library $lib was built in different versions on the different CI nodes! Versions: ${versions.distinct()}"
        }
    }
}

val generateBuildScriptsTask = tasks.register("generateBuildScripts")
for (pkg in packages) {
    if (!isPublishing || pkg.isPublished) continue

    val baseWrappersPath = File(wrappersPath, "static")
    val pkgPath = File(baseWrappersPath, pkg.name)

    val libsPath = File(pkgPath, "libs")
    val libTargets = libsPath.listFiles().orEmpty().filter { !it.name.startsWith(".") }
    val projectTargets = libTargets.mapNotNull { BuildTarget.valueOf(it.name).takeIf { it.isNative() } }
    val libNames = File(libTargets.firstOrNull() ?: continue, "lib").listFiles().orEmpty().filter {
        it.extension in listOf("a", "lib", "so", "dylib", "dll")
    }.map {
        it.nameWithoutExtension
    }.toSet()

    fun copyDynamicLib(pkgDir: File, libName: String, exclude: Set<File>): Set<File> {
        val targetsMap = mutableMapOf<String, String>()
        val result = mutableSetOf<File>()
        for (target in projectTargets) {
            if (!target.dynamicLib) continue
            val sharedLibs =
                File(wrappersPath, "dynamic/${pkg.name}/libs/${target.name}/lib").listFiles().orEmpty().filter {
                    it !in exclude && it.nameWithoutExtension.startsWith(libName) &&
                        (it.extension in listOf("so", "dylib", "dll") || it.name.endsWith(".dll.a"))
                }.takeIf { it.isNotEmpty() } ?: error("Could not find shared lib file for: $libName")
            result.addAll(sharedLibs)
            for (sharedLib in sharedLibs) {
                val destination = if (target.androidAbi != null) {
                    File(pkgDir, "src/androidMain/jniLibs/${target.androidAbi}/${sharedLib.name}")
                } else {
                    if (!sharedLib.name.endsWith(".dll.a")) {
                        check(target.name !in targetsMap) {
                            "ERROR: Multiple library candidates: ${sharedLib.name} and ${targetsMap[target.name]}"
                        }
                        targetsMap[target.name] = sharedLib.name
                    }
                    File(pkgDir, "src/jvmMain/resources/jni/${target.name}/${sharedLib.name}")
                }
                destination.parentFile.mkdirs()
                if (destination.exists()) {
                    destination.delete()
                }
                sharedLib.copyTo(destination)
            }
        }
        for (variant in listOf("jvm", "android")) {
            val config = File(pkgDir, "src/${variant}Main/resources/META-INF/nativebuild.json")
            config.parentFile.mkdirs()
            config.writeTextIfDifferent(
                buildJsonObject {
                    put("package", pkg.name)
                    put("lib", libName)
                }.toString(),
            )
        }
        if (projectTargets.any { it.jvmDynamicLib }) {
            val className = "NativeBuildsJvmLib${libName.removePrefix("lib").toCamelCase()}"
            File(pkgDir, "src/jvmCommonMain/kotlin/com/ensody/nativebuilds/${pkg.name.lowercase()}/$className.kt")
                .writeTextIfDifferent(
                    """
                package com.ensody.nativebuilds.${pkg.name.lowercase()}

                import com.ensody.nativebuilds.loader.NativeBuildsJvmLib

                public object $className : NativeBuildsJvmLib {
                    override val packageName: String = ${pkg.name.quote()}
                    override val libName: String = ${libName.quote()}
                    override val platformFileName: Map<String, String> = mapOf(${targetsMap.entries.joinToString { (k, v) ->
                        "${k.quote()} to ${v.quote()}"
                    }})
                }
                """.trimIndent().trim() + "\n",
                )
        }
        return result
    }

    val pkgScriptTask = tasks.register("generateBuildScripts-${pkg.name}") {
        doLast {
            val copied = mutableSetOf<File>()
            for (libName in libNames.sortedByDescending { it.length }) {
                val pkgDir = File(baseWrappersPath, "${pkg.name}-$libName")
                File(pkgDir, "build.gradle.kts").writeTextIfDifferent(
                    generateBuildGradle(
                        projectName = pkg.name,
                        libName = libName,
                        version = pkg.version,
                        license = pkg.license,
                        targets = projectTargets,
                        debug = false,
                    ),
                )
                copied.addAll(copyDynamicLib(pkgDir, libName, exclude = copied))
                if (includeDebugBuilds) {
                    File(baseWrappersPath, "${pkg.name}-$libName--debug/build.gradle.kts").writeTextIfDifferent(
                        generateBuildGradle(
                            projectName = pkg.name,
                            libName = libName,
                            version = pkg.version,
                            license = pkg.license,
                            targets = projectTargets,
                            debug = true,
                        ),
                    )
                }
            }
        }
    }
    generateBuildScriptsTask.dependsOn(pkgScriptTask)

    val headersPkgName = "${pkg.name}-headers"
    // Sometimes the headers are different per target (e.g. OpenSSL's configuration.h).
    for (child in libTargets) {
        val (artifactName, zipTask) = registerZipTask(headersPkgName, child)
        publishing {
            publications {
                create<MavenPublication>(artifactName) {
                    artifactId = artifactName
                    groupId = GroupId
                    version = pkg.version
                    pom {
                        licenses {
                            license {
                                name = pkg.license.longName
                                url = pkg.license.url
                            }
                        }
                    }
                    artifact(zipTask)
                }
            }
        }
    }

    publishing {
        publications {
            create<MavenPublication>(headersPkgName) {
                artifactId = headersPkgName
                groupId = GroupId
                version = pkg.version
                pom {
                    licenses {
                        license {
                            name = pkg.license.longName
                            url = pkg.license.url
                        }
                    }
                }
            }
        }
    }
}

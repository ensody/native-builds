import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.ensody.buildlogic.BuildPackage
import com.ensody.buildlogic.BuildTarget
import com.ensody.buildlogic.GroupId
import com.ensody.buildlogic.OS
import com.ensody.buildlogic.cli
import com.ensody.buildlogic.generateBuildGradle
import com.ensody.buildlogic.json
import com.ensody.buildlogic.loadBuildPackages
import com.ensody.buildlogic.registerZipTask
import com.ensody.buildlogic.renameLeafName
import com.ensody.buildlogic.setupBuildLogic
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

plugins {
    id("com.ensody.build-logic.base")
    id("com.ensody.build-logic.publish")
}

setupBuildLogic {}

val nativeBuildPath = layout.buildDirectory.dir("nativebuilds").get().asFile
val wrappersPath = File("$rootDir/generated-kotlin-wrappers")
val initBuildTask = tasks.register("cleanNativeBuild") {
    doFirst {
        nativeBuildPath.deleteRecursively()

        // If we don't delete this, vcpkg will think that the package might already be installed and skip the output
        // to x-packages-root.
        File("$rootDir/vcpkg_installed").deleteRecursively()
    }
}

// This is used to publish a new version in case the build script has changed fundamentally
val rebuildVersionWithSuffix = mapOf<String, Map<String, String>>(
    "curl" to mapOf("8.15.0" to ".1"),
    "lz4" to mapOf("1.10.0" to ".1"),
    "nghttp2" to mapOf("1.66.0" to ".1"),
    "nghttp3" to mapOf("1.11.0" to ".1"),
    "ngtcp2" to mapOf("1.14.0" to ".1"),
    "openssl" to mapOf("3.5.2" to ".1"),
    "zlib" to mapOf("1.3.1" to ".1"),
    "zstd" to mapOf("1.5.7" to ".1"),
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

        OS.Linux -> listOf(
            BuildTarget.linuxX64,
            BuildTarget.linuxArm64,

            BuildTarget.androidNativeArm64,
            BuildTarget.androidNativeArm32,
            BuildTarget.androidNativeX64,
            BuildTarget.androidNativeX86,
        )

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
            cli(
                "./vcpkg/vcpkg",
                "install",
                "--triplet",
                target.triplet,
                "--x-packages-root",
                "$nativeBuildPath/${target.name}",
                inheritIO = true,
            )
            for (pkg in packages) {
                if (pkg.isPublished) continue

                val sourceDir = layout.buildDirectory.dir("nativebuilds/${target.name}/${pkg.name}_${target.triplet}")
                val destPath = File(wrappersPath, "${pkg.name}/libs/${target.name}")
                copy {
                    from(sourceDir) {
                        include("include/**", "lib/**")
                        if (System.getenv("INCLUDE_DEBUG_BUILDS") == "true") {
                            include("debug/**")
                        }
                        exclude("debug/lib/pkgconfig", "lib/pkgconfig")
                    }
                    into(destPath)
                }
                File(destPath, "lib/libzlib.a").renameLeafName("libz.a")
                File(destPath, "debug/lib/libcurl-d.a").renameLeafName("libcurl.a")
                File(destPath, "debug/lib/liblz4d.a").renameLeafName("liblz4.a")
            }
        }
    }
    assembleTask.dependsOn(assemble)
}

if (System.getenv("PUBLISHING") == "true") {
    wrappersPath.listFiles().orEmpty().filter { it.name.startsWith("pkg-") && it.extension == "json" }.flatMap {
        json.decodeFromString<List<BuildPackage>>(it.readText()).map { it.name to it.version }
    }.groupBy({ it.first }, { it.second }).forEach { (lib, versions) ->
        check(versions.toSet().size == 1) {
            "Library $lib was built in different versions on the different CI nodes! Versions: ${versions.distinct()}"
        }
    }
} else {
    File(wrappersPath, "pkg-${OS.current.name}-$splitId.json").writeTextIfDifferent(json.encodeToString(packages))
}

val generateBuildScriptsTask = tasks.register("generateBuildScripts")
for (pkg in packages) {
    if (System.getenv("PUBLISHING") != "true" || pkg.isPublished) continue

    val pkgPath = File(wrappersPath, pkg.name)

    val libsPath = File(pkgPath, "libs")
    val libTargets = libsPath.listFiles().orEmpty().filter { !it.name.startsWith(".") }
    val projectTargets = libTargets.filter { BuildTarget.valueOf(it.name).isNative() }
    val libNames = File(libTargets.firstOrNull() ?: continue, "lib").listFiles().orEmpty().filter {
        it.extension in listOf("a", "lib")
    }.map {
        it.nameWithoutExtension
    }.toSet()

    val pkgScriptTask = tasks.register("generateBuildScripts-${pkg.name}") {
        doLast {
            if (libNames.size == 1) {
                File(pkgPath, "build.gradle.kts").writeTextIfDifferent(
                    generateBuildGradle(
                        projectName = pkg.name,
                        libName = libNames.single(),
                        version = pkg.version,
                        license = pkg.license,
                        targets = projectTargets,
                        includeZip = true,
                        debug = false,
                    ),
                )
                if (System.getenv("INCLUDE_DEBUG_BUILDS") == "true") {
                    File(wrappersPath, "${pkg.name}--debug/build.gradle.kts").writeTextIfDifferent(
                        generateBuildGradle(
                            projectName = pkg.name,
                            libName = libNames.single(),
                            version = pkg.version,
                            license = pkg.license,
                            targets = projectTargets,
                            includeZip = false,
                            debug = true,
                        ),
                    )
                }
            } else {
                for (libName in libNames) {
                    File(wrappersPath, "${pkg.name}-$libName/build.gradle.kts").writeTextIfDifferent(
                        generateBuildGradle(
                            projectName = pkg.name,
                            libName = libName,
                            version = pkg.version,
                            license = pkg.license,
                            targets = projectTargets,
                            includeZip = false,
                            debug = false,
                        ),
                    )
                    if (System.getenv("INCLUDE_DEBUG_BUILDS") == "true") {
                        File(wrappersPath, "${pkg.name}-$libName--debug/build.gradle.kts").writeTextIfDifferent(
                            generateBuildGradle(
                                projectName = pkg.name,
                                libName = libName,
                                version = pkg.version,
                                license = pkg.license,
                                targets = projectTargets,
                                includeZip = false,
                                debug = true,
                            ),
                        )
                    }
                }
            }
        }
    }
    generateBuildScriptsTask.dependsOn(pkgScriptTask)

    val remainingTargets = if (libNames.size > 1) libTargets else libTargets - projectTargets
    for (child in remainingTargets) {
        val (artifactName, zipTask) = registerZipTask(pkg.name, child)
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

    if (libNames.size > 1) {
        // There is no Gradle project for pkg.name, so create a minimal POM publication
        publishing {
            publications {
                create<MavenPublication>(pkg.name) {
                    artifactId = pkg.name
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
}

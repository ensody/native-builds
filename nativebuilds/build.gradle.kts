import android.databinding.tool.ext.toCamelCase
import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.ensody.buildlogic.BuildPackage
import com.ensody.buildlogic.BuildTarget
import com.ensody.buildlogic.GroupId
import com.ensody.buildlogic.PkgDef
import com.ensody.buildlogic.OS
import com.ensody.buildlogic.cli
import com.ensody.buildlogic.generateBuildGradle
import com.ensody.buildlogic.json
import com.ensody.buildlogic.loadBuildPackages
import com.ensody.buildlogic.normalizeNewlines
import com.ensody.buildlogic.registerZipTask
import com.ensody.buildlogic.renameLeafName
import com.ensody.buildlogic.setupBuildLogic
import io.ktor.http.quote
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.Family

plugins {
    id("com.ensody.build-logic.base")
    id("com.ensody.build-logic.publish")
}

setupBuildLogic {}

val pkgGraph = listOf(
    PkgDef(
        pkg = "brotli",
        sublibDependencies = mapOf(
            "libbrotlicommon" to listOf(),
            "libbrotlidec" to listOf("libbrotlicommon"),
            "libbrotlienc" to listOf("libbrotlicommon"),
        ),
        republishVersionSuffix = mapOf("1.2.0" to "4"),
    ),
    PkgDef(
        pkg = "curl",
        sublibDependencies = mapOf(
            "libcurl" to listOf("libcrypto", "libssl", "libnghttp2", "libnghttp3", "libtcp2", "libz"),
        ),
        republishVersionSuffix = mapOf("8.18.0" to "5"),
    ),
    PkgDef(
        pkg = "lz4",
        sublibDependencies = mapOf(
            "liblz4" to listOf(),
        ),
        republishVersionSuffix = mapOf("1.10.0" to ".8"),
    ),
    PkgDef(
        pkg = "nghttp2",
        sublibDependencies = mapOf(
            "libnghttp2" to listOf(),
        ),
        republishVersionSuffix = mapOf("1.68.0" to ".8"),
    ),
    PkgDef(
        pkg = "nghttp3",
        sublibDependencies = mapOf(
            "libnghttp3" to listOf(),
        ),
        republishVersionSuffix = mapOf("1.14.0" to "4"),
    ),
    PkgDef(
        pkg = "ngtcp2",
        sublibDependencies = mapOf(
            "libngtcp2" to listOf(),
            "libngtcp2_crypto_ossl" to listOf("libngtcp2", "libcrypto"),
        ),
        republishVersionSuffix = mapOf("1.19.0" to "4"),
    ),
    PkgDef(
        pkg = "openssl",
        sublibDependencies = mapOf(
            "libcrypto" to listOf(),
            "libssl" to listOf("libcrypto"),
        ),
        republishVersionSuffix = mapOf("3.6.0" to ".13"),
    ),
    PkgDef(
        pkg = "zlib",
        sublibDependencies = mapOf(
            "libz" to listOf(),
        ),
        republishVersionSuffix = mapOf("1.3.1" to ".8"),
    ),
    PkgDef(
        pkg = "zstd",
        sublibDependencies = mapOf(
            "libzstd" to listOf(),
        ),
        republishVersionSuffix = mapOf("1.5.7" to ".8"),
    ),
).associateBy { it.pkg }

// TODO: Debug builds will have to be done via overlays. They're not fully supported yet.
val includeDebugBuilds = System.getenv("INCLUDE_DEBUG_BUILDS") == "true"
val isPublishing = System.getenv("PUBLISHING") == "true"

val nativeBuildPath = layout.buildDirectory.dir("nativebuilds").get().asFile
val deduplicatedHeadersBasePath = layout.buildDirectory.dir("nativebuilds-headers").get().asFile
val overlayTriplets = layout.buildDirectory.dir("nativebuilds-triplets").get().asFile
val overlayToolchains = layout.buildDirectory.dir("nativebuilds-toolchains").get().asFile
val wrappersPath = File(rootDir, "generated-kotlin-wrappers")
val initBuildTask = tasks.register("cleanNativeBuild") {
    dependsOn(project(":kotlin-native-setup").tasks.named("assemble"))

    doLast {
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

        overlayToolchains.deleteRecursively()
        overlayToolchains.mkdirs()

        val communityTriplets = File("$rootDir/vcpkg/triplets/community")
        val baseTriplets = (communityTriplets.listFiles()!! + communityTriplets.parentFile.listFiles()!!).filter {
            it.isFile && it.extension == "cmake"
        }
        for (target in BuildTarget.entries) {
            val file = baseTriplets.first { it.nameWithoutExtension == target.triplet }
            val destination = File(overlayTriplets, file.name)
            file.copyTo(destination)
            destination.appendText("\nset(VCPKG_BUILD_TYPE release)\n")
            var toolchainSetup = mutableListOf<String>()
            if (target.isAndroid()) {
                // The vcpkg default triplets use Android API level 28.
                // Change that to use the same Android API level as Kotlin.
                // https://github.com/JetBrains/kotlin/blob/v2.3.0/native/utils/src/org/jetbrains/kotlin/konan/target/ClangArgs.kt#L11
                toolchainSetup += "set(VCPKG_CMAKE_SYSTEM_VERSION 21)"
            }

            // Kotlin Native uses its own toolchain. For example, on Linux it uses its own glibc version.
            // If vcpkg compiles against the host system glibc this might make the resulting static/shared lib depend on
            // symbols that are only available on more recent glibc versions. This causes two problems:
            // 1. Any consumer of that static/shared lib would fail building with Kotlin Native.
            // 2. The minimum supported glibc versions might be too new for most real-world Linux machines.
            // So, in order to ensure compatibility, we reconfigure vcpkg to use Kotlin Native's own Linux toolchain.
            val sourceToolchain = target.sourceToolchain?.let { file("toolchains/$it") }
            if (sourceToolchain != null) {
                val konanDataDir = System.getenv("KONAN_DATA_DIR")?.takeIf { it.isNotBlank() }
                val distribution = Distribution(
                    konanHome = project(":kotlin-native-setup").properties["konanHome"] as String,
                    konanDataDir = konanDataDir,
                )
                val konanTarget = target.konanTarget!!
                val toolchainKey = if (konanTarget.family == Family.LINUX) {
                    "toolchainDependency.${konanTarget.name}"
                } else {
                    "llvm.${konanTarget.name}.user"
                }
                val toolchainName = distribution.properties.getProperty(toolchainKey)
                val toolchainDirectory = File(distribution.dependenciesDir, toolchainName)
                val env = mutableMapOf<String, String>(
                    "VCPKG_ROOT" to File(rootDir, "vcpkg").absolutePath,
                    "TOOLCHAIN_DIR" to toolchainDirectory.absolutePath,
                )
                if (konanTarget.family == Family.LINUX) {
                    env["TOOLCHAIN_TARGET"] = distribution.properties.getProperty("targetTriple.${konanTarget.name}")
                }

                val destinationToolchain = File(overlayToolchains, "${konanTarget.name}.cmake")
                var toolchainCode = sourceToolchain.readText()
                // Workaround for Windows having problems with $ENV{...} access.
                // Resolve envs statically and create per-target toolchain files for Linux and Windows.
                for ((key, value) in env) {
                    toolchainCode = toolchainCode.replace($$"$ENV{$$key}", value.quote().drop(1).dropLast(1))
                }
                destinationToolchain.writeText(toolchainCode)
                toolchainSetup += "set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE ${destinationToolchain.absolutePath.quote()})"
            }
            destination.appendText("\n" + toolchainSetup.joinToString("\n") + "\n")

            if (target.dynamicLib) {
                val libFile = baseTriplets.first { it.nameWithoutExtension == target.baseDynamicTriplet }
                val dynamic = File(overlayTriplets, "${target.dynamicTriplet}.cmake")
                libFile.copyTo(dynamic)
                dynamic.appendText("\nset(VCPKG_CRT_LINKAGE dynamic)\nset(VCPKG_LIBRARY_LINKAGE dynamic)\n")
                dynamic.appendText("\n" + toolchainSetup.joinToString("\n") + "\n")
            }
        }
    }
}

val packages = loadBuildPackages(rootDir).map { pkg ->
    pkgGraph[pkg.name]?.republishVersionSuffix?.get(pkg.version)?.let {
        val separator = if (it[0].isDigit()) "_" else ""
        pkg.copy(version = "${pkg.version}$separator$it")
    } ?: pkg
}
println(packages.joinToString("\n") { "$it" })

val splits = System.getenv("MAX_SPLITS")?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
val splitId = System.getenv("BUILD_SPLIT_ID")?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
val targets = System.getenv("BUILD_TARGETS")?.takeIf { it.isNotBlank() }?.split(",")?.map {
    BuildTarget.valueOf(it)
}?.distinct()
    ?: BuildTarget.entries.mapNotNull { target ->
        val os = when (target) {
            BuildTarget.iosArm64,
            BuildTarget.iosSimulatorArm64,
            BuildTarget.iosX64,
            BuildTarget.tvosArm64,
            BuildTarget.tvosSimulatorArm64,
            BuildTarget.tvosX64,
            BuildTarget.watchosDeviceArm64,
            BuildTarget.watchosArm64,
            BuildTarget.watchosArm32,
            BuildTarget.watchosSimulatorArm64,
            BuildTarget.watchosX64,
            BuildTarget.macosArm64,
            BuildTarget.macosX64,
            -> OS.macOS

            BuildTarget.linuxArm64,
            BuildTarget.linuxX64,
            -> OS.Linux

            BuildTarget.androidNativeArm64,
            BuildTarget.androidNativeArm32,
            BuildTarget.androidNativeX64,
            BuildTarget.androidNativeX86,
            -> OS.Linux

            BuildTarget.mingwX64,
            -> OS.Windows

            BuildTarget.windowsX64,
            BuildTarget.wasm32,
            -> null
        }
        target.takeIf { OS.current == os }
    }.run {
        val chunkSize = size / splits
        slice(chunkSize * splitId until if (splitId >= splits - 1) size else chunkSize * (splitId + 1))
    }

println("Building for targets: ${targets.joinToString(", ") { it.name }}")

val assembleTask = tasks.register("assembleProjects") {
    dependsOn("cleanNativeBuild")
}.get()
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
                    File(baseNativeBuildPath, target.name).absolutePath,
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

    val pkgDef = pkgGraph.getValue(pkg.name)

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
    // TODO: We could handle this case by auto-renaming the respective module, so it won't clash with the headers module
    //  but this is so extremely unlikely that we simply detect this case and fail, so then can handle it once necessary
    check("headers" !in libNames) {
        "Package ${pkg.name} contains a lib without \"lib\" prefix that is named just \"headers\""
    }

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
                    putJsonObject("platformFileName") {
                        for ((k, v) in targetsMap) put(k, v)
                    }
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
                    override val platformFileName: Map<String, String> = mapOf(${
                        targetsMap.entries.joinToString { (k, v) ->
                            "${k.quote()} to ${v.quote()}"
                        }
                    })
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
                val libDependencies = pkgDef.sublibDependencies.getValue(libName)
                val pkgDir = File(baseWrappersPath, "${pkg.name}-$libName")
                when {
                    pkg.name == "openssl" && libName == "libcrypto" -> {
                        File(pkgDir, "cinterop.def").writeTextIfDifferent("linkerOpts.android_x86 = -latomic")
                    }
                }
                File(pkgDir, "build.gradle.kts").writeTextIfDifferent(
                    generateBuildGradle(
                        pkgDef = pkgDef,
                        libName = libName,
                        version = pkg.version,
                        license = pkg.license,
                        targets = projectTargets,
                        debug = false,
                    ),
                )
                copied.addAll(copyDynamicLib(pkgDir, libName, exclude = copied))

                if (libDependencies.isEmpty()) {
                    val className = "BuildTest"
                    File(
                        pkgDir,
                        "src/commonTest/kotlin/com/ensody/nativebuilds/${pkg.name.lowercase()}/$className.kt",
                    )
                        .writeTextIfDifferent(
                            """
                package com.ensody.nativebuilds.${pkg.name.lowercase()}

                import kotlin.test.Test

                internal class $className {
                    @Test
                    fun buildTest() {
                    }
                }
                """.trimIndent().trim() + "\n",
                        )
                }

                if (includeDebugBuilds) {
                    File(baseWrappersPath, "${pkg.name}-$libName--debug/build.gradle.kts").writeTextIfDifferent(
                        generateBuildGradle(
                            pkgDef = pkgDef,
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
    // Sometimes the headers are different per target (e.g. OpenSSL's configuration.h). Deduplicate all common headers
    // into a separate "common" folder within the zip file and the remaining
    val headers = libTargets.associateWith { targetOutDir ->
        val includeDir = File(targetOutDir, "include")
        includeDir.walkBottomUp().filter { it.isFile }.map { it.absoluteFile.relativeTo(includeDir) }.toSet()
    }
    val commonFilePaths = headers.values.fold(headers.values.firstOrNull().orEmpty()) { acc, files ->
        acc.intersect(files)
    }
    val baseIncludes = File(libTargets.first(), "include")
    val identicalFiles = commonFilePaths.filter { relativePath ->
        val content = File(baseIncludes, relativePath.path).readText().normalizeNewlines()
        libTargets.minus(libTargets.first()).all {
            File(it, "include/${relativePath.path}").readText().normalizeNewlines() == content
        }
    }
    val deduplicatedPath = File(deduplicatedHeadersBasePath, pkg.name)
    deduplicatedPath.deleteRecursively()
    for (relativePath in identicalFiles) {
        val content = File(baseIncludes, relativePath.path).readText().normalizeNewlines()
        File(deduplicatedPath, "common/${relativePath.path}").writeTextIfDifferent(content)
    }
    for ((targetOutDir, paths) in headers) {
        val outDir = File(deduplicatedPath, targetOutDir.name)
        for (relativePath in paths - identicalFiles.toSet()) {
            val content = File(targetOutDir, "include/${relativePath.path}").readText()
            File(outDir, relativePath.path).writeTextIfDifferent(content)
        }
    }
    val zipTask = registerZipTask(headersPkgName, deduplicatedPath)
    publishing {
        publications {
            create<MavenPublication>(headersPkgName) {
                artifactId = headersPkgName
                groupId = GroupId
                version = pkg.version
                artifact(zipTask)
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

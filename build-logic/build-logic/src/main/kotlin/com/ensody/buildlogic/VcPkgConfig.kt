@file:OptIn(ExperimentalSerializationApi::class)

package com.ensody.buildlogic

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

val GroupId = "com.ensody.nativebuilds"

val json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

val client = HttpClient(OkHttp) {
    expectSuccess = false
}

private class BuildRegistry(val root: File) {
    val configsCache = mutableMapOf<String, VcPkgConfig>()
    val packages = mutableMapOf<String, BuildPackage>()

    fun load(dependency: VcPkgDependency) {
        load(dependency, isRoot = true)
    }

    private fun load(dependency: VcPkgDependency, isRoot: Boolean) {
        if (dependency.host) return

        val config = configsCache.getOrPut(dependency.name) {
            VcPkgConfig.load(File(root, "vcpkg/ports/${dependency.name}/vcpkg.json"))
        }
        val pkg = packages.getOrPut(dependency.name) {
            val features = config.defaultFeatures.takeIf {
                isRoot && dependency.defaultFeatures || !isRoot
            }.orEmpty() + dependency.features
            BuildPackage(
                name = dependency.name,
                version = config.version,
                features = config.resolveFeatures(features.map { it.name }).sorted(),
                config = config,
            )
        }
        if (!isRoot) {
            resolve(pkg)
        }
    }

    fun resolve() {
        for (pkg in packages.values.toList()) {
            resolve(pkg)
        }
    }

    private fun resolve(pkg: BuildPackage) {
        val deps = pkg.config.dependencies.filter { !it.host }.associateBy { it.name }.toMutableMap()
        for (feature in pkg.features) {
            deps.putAll(
                pkg.config.features.getValue(feature).dependencies
                    .filter { it.name != pkg.name && !it.host }
                    .associateBy { it.name },
            )
        }
        for (dep in deps.values) {
            load(dep, isRoot = false)
        }
    }
}

fun loadBuildPackages(root: File): List<BuildPackage> {
    val rootConfig = VcPkgConfig.load(File(root, "vcpkg.json"))
    val registry = BuildRegistry(root)
    for (dependency in rootConfig.dependencies) {
        registry.load(dependency)
    }
    registry.resolve()
    return registry.packages.values.sortedBy { it.name }
}

@Serializable
data class BuildPackage(
    val name: String,
    val version: String,
    val features: List<String>,
    val config: VcPkgConfig,
) {
    @Transient
    val license: License = License.get(config.license)

    val isPublished: Boolean by lazy {
        isPublished(null)
    }

    fun isPublished(target: BuildTarget?): Boolean =
        runBlocking {
            val targetName = target?.let { "$name-${it.name}" } ?: "$name-headers"
            val groupPath = GroupId.replace(".", "/")
            client.get("https://repo1.maven.org/maven2/$groupPath/$targetName/$version/$targetName-$version.pom")
                .status.value == 200
        }

    override fun toString(): String =
        "${this::class.simpleName}(name=$name, version=$version, features=${features.sorted()})"
}

enum class License(val id: String, val longName: String, val url: String) {
    Apache2(
        "Apache-2.0",
        "The Apache Software License, Version 2.0",
        "https://www.apache.org/licenses/LICENSE-2.0.txt",
    ),
    BSD2("BSD-2-Clause", "BSD 2-Clause", "https://opensource.org/license/BSD-2-Clause"),
    BSD3("BSD-3-Clause", "BSD 3-Clause", "https://opensource.org/license/BSD-3-Clause"),
    MIT("MIT", "The MIT License", "https://opensource.org/license/mit"),
    MIT_CMU("MIT-CMU", "CMU License", "https://spdx.org/licenses/MIT-CMU.html"),
    Curl("curl", "curl License", "https://spdx.org/licenses/curl.html"),
    ZLib("Zlib", "zlib License", "https://www.zlib.net/zlib_license.html"),
    ;

    companion object {
        fun get(licenseId: String): License =
            values().find { it.id == licenseId }
                ?: values().find { it.id == licenseId.substringBefore(" OR ").substringBefore(" AND ") }
                ?: error("License not found for id: $licenseId")
    }
}

enum class BuildTarget(
    val triplet: String,
    val konanTarget: KonanTarget?,
    val baseDynamicTriplet: String = triplet,
    val dynamicTriplet: String = baseDynamicTriplet.removeSuffix("-dynamic") + "-dynamic",
    val sourceToolchain: String? = null,
    val androidAbi: String? = null,
    val jvmDynamicLib: Boolean = false,
) {
    iosArm64("arm64-ios", KonanTarget.IOS_ARM64),
    iosSimulatorArm64("arm64-ios-simulator", KonanTarget.IOS_SIMULATOR_ARM64),
    iosX64("x64-ios", KonanTarget.IOS_X64),

    watchosDeviceArm64("arm64-watchos", KonanTarget.WATCHOS_DEVICE_ARM64),
    watchosArm64("arm6432-watchos", KonanTarget.WATCHOS_ARM64),
    watchosArm32("arm-watchos", KonanTarget.WATCHOS_ARM32),
    watchosSimulatorArm64("arm64-watchos-simulator", KonanTarget.WATCHOS_SIMULATOR_ARM64),
    watchosX64("x64-watchos-simulator", KonanTarget.WATCHOS_X64),

    tvosArm64("arm64-tvos", KonanTarget.TVOS_ARM64),
    tvosSimulatorArm64("arm64-tvos-simulator", KonanTarget.TVOS_SIMULATOR_ARM64),
    tvosX64("x64-tvos-simulator", KonanTarget.TVOS_X64),

    androidNativeArm64("arm64-android", KonanTarget.ANDROID_ARM64, androidAbi = "arm64-v8a"),
    androidNativeArm32("arm-neon-android", KonanTarget.ANDROID_ARM32, androidAbi = "armeabi-v7a"),
    androidNativeX64("x64-android", KonanTarget.ANDROID_X64, androidAbi = "x86_64"),
    androidNativeX86("x86-android", KonanTarget.ANDROID_X86, androidAbi = "x86"),

    macosArm64("arm64-osx", KonanTarget.MACOS_ARM64, jvmDynamicLib = true),
    macosX64("x64-osx", KonanTarget.MACOS_X64, jvmDynamicLib = true),

    linuxArm64("arm64-linux", KonanTarget.LINUX_ARM64, sourceToolchain = "linux.cmake", jvmDynamicLib = true),
    linuxX64("x64-linux", KonanTarget.LINUX_X64, sourceToolchain = "linux.cmake", jvmDynamicLib = true),

    mingwX64("x64-mingw-static", KonanTarget.MINGW_X64, dynamicTriplet = "x64-mingw-dynamic", sourceToolchain = "mingw.cmake", jvmDynamicLib = true),
    windowsX64("x64-windows-static", null, jvmDynamicLib = true),

    wasm32("wasm32-emscripten", null),
    ;

    val dynamicLib: Boolean = androidAbi != null || jvmDynamicLib

    fun isNative(): Boolean =
        when (this) {
            iosArm64,
            iosSimulatorArm64,
            iosX64,
            watchosDeviceArm64,
            watchosArm64,
            watchosArm32,
            watchosSimulatorArm64,
            watchosX64,
            tvosArm64,
            tvosSimulatorArm64,
            tvosX64,
            androidNativeArm64,
            androidNativeArm32,
            androidNativeX64,
            androidNativeX86,
            macosArm64,
            macosX64,
            linuxArm64,
            linuxX64,
            mingwX64,
            windowsX64 -> true
            wasm32 -> false
        }

    fun isAndroid(): Boolean =
        when (this) {
            androidNativeArm64,
            androidNativeArm32,
            androidNativeX64,
            androidNativeX86 -> true
            iosArm64,
            iosSimulatorArm64,
            iosX64,
            watchosDeviceArm64,
            watchosArm64,
            watchosArm32,
            watchosSimulatorArm64,
            watchosX64,
            tvosArm64,
            tvosSimulatorArm64,
            tvosX64,
            macosArm64,
            macosX64,
            linuxArm64,
            linuxX64,
            mingwX64,
            windowsX64,
            wasm32 -> false
        }
}

@Serializable
data class VcPkgConfig(
    val name: String,
    @JsonNames("version", "version-semver", "version-date")
    val version: String,
    val dependencies: List<VcPkgDependency> = emptyList(),
    val features: Map<String, VcPkgFeature> = emptyMap(),
    val license: String = "",
    @SerialName("default-features")
    val defaultFeatures: List<VcPkgDefaultFeature> = emptyList(),
) {
    fun resolveFeatures(features: List<String>): Set<String> {
        val toProcess = features.toMutableSet()
        val result = mutableSetOf<String>()
        while (toProcess.isNotEmpty()) {
            val feature = toProcess.first()
            toProcess.remove(feature)
            result.add(feature)
            val subfeatures = this.features.getValue(feature).dependencies.filter { it.name == name }.flatMap {
                it.features.map { it.name }
            }.toSet() - result
            toProcess.addAll(subfeatures)
        }
        return result
    }

    companion object {
        fun load(file: File): VcPkgConfig =
            json.decodeFromString(file.readText())
    }
}

@Serializable(with = VcPkgDependencySerializer::class)
@KeepGeneratedSerializer
data class VcPkgDependency(
    val name: String,
    val host: Boolean = false,
    @SerialName("default-features")
    val defaultFeatures: Boolean = true,
    val features: List<VcPkgDefaultFeature> = emptyList(),
)

@Serializable(with = VcPkgDefaultFeatureSerializer::class)
@KeepGeneratedSerializer
data class VcPkgDefaultFeature(
    val name: String,
    val platform: String? = null,
)

@Serializable
data class VcPkgFeature(
    val description: String,
    val dependencies: List<VcPkgDependency> = emptyList(),
)

internal object VcPkgDependencySerializer : KSerializer<VcPkgDependency> {
    override val descriptor: SerialDescriptor =
        SerialDescriptor("VcPkgDependency", VcPkgDependency.generatedSerializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: VcPkgDependency,
    ) {
        encoder.encodeSerializableValue(VcPkgDependency.generatedSerializer(), value)
    }

    override fun deserialize(decoder: Decoder): VcPkgDependency {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        return if (element is JsonPrimitive && element.isString) {
            VcPkgDependency(element.content, host = false, defaultFeatures = true, features = emptyList())
        } else {
            decoder.json.decodeFromJsonElement(VcPkgDependency.generatedSerializer(), element)
        }
    }
}

internal object VcPkgDefaultFeatureSerializer : KSerializer<VcPkgDefaultFeature> {
    override val descriptor: SerialDescriptor =
        SerialDescriptor("VcPkgDefaultFeature", VcPkgDefaultFeature.generatedSerializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: VcPkgDefaultFeature,
    ) {
        encoder.encodeSerializableValue(VcPkgDefaultFeature.generatedSerializer(), value)
    }

    override fun deserialize(decoder: Decoder): VcPkgDefaultFeature {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        return if (element is JsonPrimitive && element.isString) {
            VcPkgDefaultFeature(element.content)
        } else {
            decoder.json.decodeFromJsonElement(VcPkgDefaultFeature.generatedSerializer(), element)
        }
    }
}

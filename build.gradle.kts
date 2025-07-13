import com.ensody.buildlogic.OS
import com.ensody.buildlogic.cli
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

plugins {
    id("com.ensody.build-logic.base")
    alias(libs.plugins.maven.publish)
}

enum class License(val id: String, val longName: String, val url: String) {
    Apache2(
        "Apache-2.0",
        "The Apache Software License, Version 2.0",
        "https://www.apache.org/licenses/LICENSE-2.0.txt",
    ),
    MIT("MIT", "The MIT License", "https://opensource.org/licenses/mit-license.php"),
    Curl("curl", "curl License", "https://spdx.org/licenses/curl.html"),
    ZLib("Zlib", "zlib License", "https://www.zlib.net/zlib_license.html"),
    ;

    companion object {
        fun get(licenseId: String): License =
            values().find { it.id == licenseId }
                ?: error("License not found for id: $licenseId")
    }
}

val GroupId = "com.ensody.nativebuilds"

val client = HttpClient(OkHttp) {
    expectSuccess = false
}

val pythonVersion = cli("python3", "--version").removePrefix("Python ").replace(Regex("""[^\d\.A-Za-z\-]"""), "_")
val venvPath = file(".venv/$pythonVersion")

val venvsRoot = venvPath.parentFile
for (child in venvsRoot.listFiles().orEmpty()) {
    if (child == venvPath) continue
    child.deleteRecursively()
}
if (!venvPath.exists()) {
    venvPath.parentFile.mkdirs()
    cli("python3", "-m", "venv", "$venvPath", inheritIO = true)
}
val venvBin = "$venvPath/" + if (OS.current == OS.Windows) "Scripts" else "bin"
cli("$venvBin/pip", "install", "-U", "conan", inheritIO = true)
runCatching { cli("$venvBin/conan", "profile", "detect") }

val publicationGroup = listOf("libcurl", "libnghttp2", "openssl", "zlib")
val pkg = publicationGroup.joinToString("-")

val versionsRaw = cli("$venvBin/conan", "search", publicationGroup.first(), "-f", "json", "-v", "quiet")
val pkgVersion = Json.decodeFromString<JsonObject>(versionsRaw).jsonObject
    .getValue("conancenter").jsonObject
    .keys.last()
    .split("/").last()
val graphRaw =
    cli("$venvBin/conan", "graph", "info", "packages/$pkg", "--version=$pkgVersion", "-f", "json", "-v", "quiet")
val artifacts = Json.decodeFromString<JsonObject>(graphRaw)
    .getValue("graph").jsonObject
    .getValue("nodes").jsonObject
    .values.mapNotNull { nodes ->
        nodes.jsonObject.run {
            getValue("name").jsonPrimitive.contentOrNull?.takeIf { println("$it: ${it in publicationGroup}"); it in publicationGroup }?.let { name ->
                val version = getValue("version").jsonPrimitive.contentOrNull
                version?.let {
                    Artifact(
                        pkg = pkg,
                        name = name,
                        version = version,
                        license = License.get(getValue("license").jsonPrimitive.content),
                    )
                }
            }
        }
    }

val targets = System.getenv("BUILD_TARGETS")?.takeIf { it.isNotBlank() }?.split(",") ?: when (OS.current) {
    OS.macOS -> listOf(
        "ios-device-arm64",
        "ios-simulator-arm64",
        "ios-simulator-x64",
        "tvos-device-arm64",
        "tvos-simulator-arm64",
        "tvos-simulator-x64",
        "watchos-device-arm32",
        "watchos-device-arm64",
        "watchos-device-arm64_32",
        "watchos-simulator-arm64",
        "watchos-simulator-x64",
        "macos-arm64",
        "macos-x64",

        "android-arm64",
        "android-arm32",
        "android-x64",
        "android-x86",
        "wasm",
    )

    OS.Linux -> listOf(
        "linux-x64",
        "linux-arm64",
    )

    OS.Windows -> listOf(
        "mingw-x64",
        "windows-x64",
    )
}
val conanPath = layout.buildDirectory.dir("conan").get().asFile
val initBuildTask = tasks.register("cleanConan") {
    doFirst {
        conanPath.deleteRecursively()
    }
}
for (artifact in artifacts) {
    if (artifact.isPublished) continue
    tasks.register("writeMetadata${artifact.name}") {
        dependsOn(initBuildTask)
        doLast {
            val root = artifact.outputDir.get().asFile
            root.mkdirs()
            File(root, "version.txt").writeText(artifact.version)
            File(root, "licenseId.txt").writeText(artifact.license.id)
        }
    }
}
val assembleTask = tasks.register("assembleAll")
for (profile in targets) {
    if (artifacts.all { it.isPublished }) continue

    val pkgProfileName = listOf(pkg, profile).joinToString("-")
    val taskName = "assemble$pkgProfileName"
    val buildTask = tasks.register(taskName) {
        dependsOn(initBuildTask)
        doLast {
            build(pkg, pkgVersion, profile)
        }
    }
    assembleTask.get().dependsOn(buildTask)

    for (artifact in artifacts) {
        if (artifact.isPublished) continue
        val zipTask = tasks.register<Zip>("zip${artifact.name}-$profile") {
            dependsOn(buildTask)
            dependsOn("writeMetadata${artifact.name}")
            archiveFileName = "$profile.zip"
            destinationDirectory = artifact.outputDir
            archiveClassifier = profile
            from(layout.buildDirectory.dir("conan/build/$pkg/$profile/output/${artifact.name}"))
        }
        assembleTask.get().dependsOn(zipTask)
    }
}

for (artifactPath in file("build/conan/artifacts").listFiles().orEmpty()) {
    val zipFiles = artifactPath.listFiles().orEmpty().filter { it.extension == "zip" }
    if (System.getenv("ASSEMBLE_ONLY") != "true" && zipFiles.isNotEmpty()) {
        val artifactName = artifactPath.name
        val artifactVersion = File(artifactPath, "version.txt").readText().trim()
        val artifactLicenseId = File(artifactPath, "licenseId.txt").readText().trim()
        val artifactLicense = License.get(artifactLicenseId)
        publishing {
            publications {
                create<MavenPublication>(artifactName) {
                    artifactId = artifactName
                    groupId = GroupId
                    version = artifactVersion
                    pom {
                        licenses {
                            license {
                                name = artifactLicense.longName
                                url = artifactLicense.url
                            }
                        }
                    }
                    for (zipFile in zipFiles) {
                        artifact(zipFile) {
                            classifier = zipFile.nameWithoutExtension
                        }
                    }
                }
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")?.isNotBlank() == true) {
        signAllPublications()
    }
    pom {
        name = rootProject.name
        description = "Native builds of popular open-source libraries"
        url = "https://github.com/ensody/native-builds"
        scm {
            url.set(this@pom.url)
        }
        developers {
            developer {
                id = "wkornewald"
                name = "Waldemar Kornewald"
                url = "https://www.ensody.com"
                organization = "Ensody GmbH"
                organizationUrl = url
            }
        }
    }
}
publishing {
    repositories {
        maven {
            name = "localMaven"
            val outputDir = File(rootDir, "build/localmaven")
            url = outputDir.toURI()
        }
    }
}

fun build(pkg: String, version: String, profile: String, shared: Boolean = false) {
    val sharedValue = if (shared) "True" else "False"
    cli(
        "$venvBin/conan",
        "install",
        "packages/$pkg",
        "--output-folder",
        layout.buildDirectory.dir("conan/build/$pkg/$profile").get().asFile.path,
        "--build=missing",
        "--version=$version",
        "-pr:b",
        "default",
        "-pr:h",
        "profiles/$profile",
        "-o",
        "*:shared=$sharedValue",
        inheritIO = true,
    )
}

data class Artifact(
    val pkg: String,
    val name: String,
    val version: String,
    val license: License,
) {
    val outputDir = layout.buildDirectory.dir("conan/artifacts/$name")

    val isPublished: Boolean =
        runBlocking {
            val groupPath = GroupId.replace(".", "/")
            client.get("https://repo1.maven.org/maven2/$groupPath/$name/$version/$name-$version.pom")
                .status.value == 200
        }
}

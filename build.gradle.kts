import com.ensody.buildlogic.BuildTarget
import com.ensody.buildlogic.GroupId
import com.ensody.buildlogic.OS
import com.ensody.buildlogic.cli
import com.ensody.buildlogic.loadBuildPackages

plugins {
    id("com.ensody.build-logic.base")
    alias(libs.plugins.maven.publish)
}

val nativeBuildPath = layout.buildDirectory.dir("nativebuilds").get().asFile
val initBuildTask = tasks.register("cleanNativeBuild") {
    doFirst {
        nativeBuildPath.deleteRecursively()
        // If we don't delete this, vcpkg will think that the package might already be installed and skip the output
        // to x-packages-root.
        layout.projectDirectory.dir("vcpkg_installed").asFile.deleteRecursively()
    }
}

val packages = loadBuildPackages(rootDir)
println(packages.joinToString("\n") { "$it" })

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

//            BuildTarget.watchosArm32,
//            BuildTarget.watchosDeviceArm64,
//            BuildTarget.watchosArm64,
//            BuildTarget.watchosSimulatorArm64,
//            BuildTarget.watchosX64,

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
            BuildTarget.windowsX64,
        )
    }

val assembleTask = tasks.register("assembleAll").get()
for (target in targets) {
    if (packages.all { it.isPublished(target) }) continue

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
        }
    }
    assembleTask.dependsOn(assemble)
    for (pkg in packages) {
        if (pkg.isPublished(target)) continue

        val artifactName = "${pkg.name}-${target.name}"
        val zipTask = tasks.register<Zip>("zip-$artifactName") {
            dependsOn(assembleTask)
            archiveFileName = "$artifactName.zip"
            destinationDirectory = layout.buildDirectory.dir("nativebuilds-artifacts")
            from(layout.buildDirectory.dir("nativebuilds/${target.name}/${pkg.name}_${target.triplet}")) {
                include("debug/**", "include/**", "lib/**")
                exclude("debug/lib/pkgconfig", "lib/pkgconfig")
            }
        }

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
}
for (pkg in packages) {
    if (pkg.isPublished || OS.current != OS.macOS) continue

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

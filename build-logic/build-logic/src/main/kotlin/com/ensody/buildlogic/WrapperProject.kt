package com.ensody.buildlogic

import io.ktor.http.quote
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

fun KotlinMultiplatformExtension.addCinterops(libProjectName: String, libFileName: String, debug: Boolean) {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops {
                create(project.name) {
                    val root =
                        File("${project.rootDir}/generated-kotlin-wrappers/static/$libProjectName/libs/${target.name}")
                    val libDir = File(root, if (debug) "debug/lib" else "lib")
                    extraOpts("-libraryPath", libDir.absolutePath)
                    extraOpts(
                        "-staticLibrary",
                        listOf("a", "lib").map { File(libDir, "$libFileName.$it") }.single { it.exists() }.name,
                    )
                    val cinteropFile = project.file("cinterop.def")
                    if (cinteropFile.exists()) {
                        definitionFile.set(cinteropFile)
                    }
                    packageName = "com.ensody.nativebuilds.kotlin.wrapper.${project.name.replace("-", ".")}"
                }
            }
        }
    }
}

fun generateBuildGradle(
    pkgDef: PkgDef,
    libName: String,
    version: String,
    license: License,
    targets: List<BuildTarget>,
    debug: Boolean,
): String {
    val libDependencies = pkgDef.sublibDependencies.getValue(libName)
    var result = """
import com.ensody.buildlogic.License
import com.ensody.buildlogic.addCinterops
import com.ensody.buildlogic.registerZipTask
import com.ensody.buildlogic.setupBuildLogic
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    ${if (targets.any { it.androidAbi != null }) "id(\"com.ensody.build-logic.android\")" else ""}
    id("com.ensody.build-logic.kmp")
    id("com.ensody.build-logic.publish")
}

version = ${version.quote()}

setupBuildLogic {
    kotlin {
        ${targets.joinToString("\n        ") { "${it.name}()" }}
        ${if (targets.any { it.jvmDynamicLib }) "jvm()" else ""}
        ${if (targets.any { it.androidAbi != null }) "androidTarget()" else ""}
        ${
        if (targets.any { it.dynamicLib }) {
            val sourceSet = when {
                targets.none { it.jvmDynamicLib } -> "android"
                targets.none { it.androidAbi != null } -> "jvm"
                else -> "jvmCommon"
            }
            """
            sourceSets["${sourceSet}Main"].dependencies {
                api(libs.nativebuilds.loader)
            }
            sourceSets["${sourceSet}Test"].dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.junit)
            }
            """.trim()
        } else {
            ""
        }
    }

        tasks.register("testAll") {
            ${if (libDependencies.isEmpty()) "dependsOn(${targets.joinToString { ("linkDebugTest" + it.name.uppercaseFirstChar()).quote() }})" else ""}
        }

        addCinterops(libProjectName = ${pkgDef.pkg.quote()}, libFileName = ${libName.quote()}, debug = $debug)
    }
}

extensions.configure<MavenPublishBaseExtension> {
    pom {
        licenses {
            license {
                name.set(License.${license.name}.longName)
                url.set(License.${license.name}.url)
            }
        }
    }
}
""".trim() + "\n"

    return result
}

fun Project.registerZipTask(libProjectName: String, child: File): Pair<String, TaskProvider<Zip>> {
    val artifactName = "$libProjectName-${child.name.lowercase()}"
    val mainZipTask = tasks.findByName("zipNativeBuilds") ?: tasks.register("zipNativeBuilds").get()
    return artifactName to tasks.register<Zip>("zip-$artifactName") {
        archiveFileName.set("$artifactName.zip")
        destinationDirectory.set(layout.buildDirectory.dir("nativebuilds-artifacts"))
        from(child) {
            include("include/**")
        }
    }.also { mainZipTask.dependsOn(it) }
}

package com.ensody.buildlogic

import io.ktor.http.quote
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

fun KotlinMultiplatformExtension.addCinterops(libProjectName: String, libFileName: String) {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops {
                create(project.name) {
                    val libDir =
                        File("${project.rootDir}/generated-kotlin-wrappers/$libProjectName/libs/${target.name}/lib")
                    extraOpts("-libraryPath", libDir.absolutePath)
                    extraOpts(
                        "-staticLibrary",
                        listOf("a", "lib").map { File(libDir, "$libFileName.$it") }.single { it.exists() }.name,
                    )
                    packageName = "com.ensody.nativebuilds.kotlin.wrapper.${project.name.replace("-", ".")}"
                }
            }
        }
    }
}

fun generateBuildGradle(
    projectName: String,
    libName: String,
    version: String,
    license: License,
    targets: List<File>,
    includeZip: Boolean,
): String {
    var result = """
import com.ensody.buildlogic.License
import com.ensody.buildlogic.addCinterops
import com.ensody.buildlogic.registerZipTask
import com.ensody.buildlogic.setupBuildLogic
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.ensody.build-logic.publish")
}

version = ${version.quote()}

setupBuildLogic {
    kotlin {
        ${targets.joinToString("\n        ") { "${it.name}()" }}

        addCinterops(${projectName.quote()}, ${libName.quote()})
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

    if (includeZip) {
        result += """
for (target in listOf(${targets.joinToString(", ") { it.name.quote() }})) {
    val child = file("libs/" + target)
    val (_, zipTask) = registerZipTask(${libName.quote()}, child)
    publishing {
        publications.named<MavenPublication>(child.name) {
            artifact(zipTask)
        }
    }
}
        """.trim() + "\n"
    }

    return result
}

fun Project.registerZipTask(libProjectName: String, child: File): Pair<String, TaskProvider<Zip>> {
    val artifactName = "$libProjectName-${child.name.lowercase()}"
    return artifactName to tasks.register<Zip>("zip-$artifactName") {
        archiveFileName.set("$artifactName.zip")
        destinationDirectory.set(layout.buildDirectory.dir("nativebuilds-artifacts"))
        from(child)
    }
}

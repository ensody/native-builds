@file:Suppress("UnstableApiUsage")

package com.ensody.buildlogic

import com.android.build.gradle.BaseExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformExtension
import org.gradle.api.plugins.catalog.CatalogPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/** Base setup. */
class BaseBuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}

fun Project.initBuildLogic() {
    group = GroupId

    initBuildLogicBase {
        setupRepositories()
    }
}

fun Project.setupRepositories() {
    repositories {
        google()
        mavenCentral()
        if (System.getenv("RUNNING_ON_CI") != "true") {
            mavenLocal()
        }
    }
}

fun Project.setupBuildLogic(block: Project.() -> Unit) {
    setupBuildLogicBase {
        setupRepositories()
        if (extensions.findByType<JavaPlatformExtension>() != null) {
            setupPlatformProject()
        }
        if (extensions.findByType<BaseExtension>() != null) {
            setupAndroid(coreLibraryDesugaring = rootLibs.findLibrary("desugarJdkLibs").get())
        }
        if (extensions.findByType<KotlinMultiplatformExtension>() != null) {
            setupKmp {}
            // testDebugUnitTest throws an error if there are no tests
            val hasTests = file("src").listFiles().orEmpty().any { sourceSet ->
                sourceSet.name.endsWith("Test") && sourceSet.walkTopDown().any { it.extension == "kt" }
            }
            tasks.register("testAll") {
                group = "verification"
                if (hasTests) {
                    dependsOn(
                        "testDebugUnitTest",
                        "jvmTest",
                        "iosSimulatorArm64Test",
                        "iosX64Test",
                        "macosArm64Test",
                        "macosX64Test",
                        "mingwX64Test",
                        "linuxX64Test",
                    )
                }
            }
        }
        if (extensions.findByType<KotlinBaseExtension>() != null) {
            setupKtLint(rootLibs.findLibrary("ktlint-cli").get())
        }
        if (extensions.findByType<KotlinJvmExtension>() != null) {
            setupKotlinJvm()
        }
        if (extensions.findByType<DetektExtension>() != null) {
            setupDetekt()
        }
        if (extensions.findByType<DokkaExtension>() != null) {
            setupDokka(copyright = "Ensody GmbH")
        }
        if (extensions.findByType<CatalogPluginExtension>() != null) {
            setupVersionCatalog()
        }
        extensions.findByType<MavenPublishBaseExtension>()?.apply {
            configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = System.getenv("RUNNING_ON_CI") == "true")
            publishToMavenCentral(automaticRelease = true)
            if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")?.isNotBlank() == true) {
                signAllPublications()
            }
            pom {
                name = if (rootProject.name.equals(project.name, true)) {
                    rootProject.name
                } else {
                    "${rootProject.name}: ${project.name}"
                }
                description = project.description?.takeIf { it.isNotBlank() }
                    ?: "Native builds of popular open-source libraries"
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
        extensions.findByType<PublishingExtension>()?.apply {
            repositories {
                maven {
                    name = "localMaven"
                    val outputDir = File(rootDir, "build/localmaven")
                    url = outputDir.toURI()
                }
            }
        }
        block()
    }
}

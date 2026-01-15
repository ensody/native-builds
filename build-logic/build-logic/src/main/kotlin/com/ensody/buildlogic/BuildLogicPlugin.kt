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
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.repositories
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
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
            setupKmp {
                sourceSets["commonTest"].dependencies {
                    implementation(rootLibs.findLibrary("kotlin-test-main").get())
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
        if (extensions.findByType<GradlePluginDevelopmentExtension>() != null) {
            setupGradlePlugin(rootLibs.findVersion("kotlinForGradlePlugins").get().toString())
        }
        extensions.findByType<MavenPublishBaseExtension>()?.apply {
            configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = System.getenv("RUNNING_ON_CI") == "true")
            publishToMavenCentral(automaticRelease = true, validateDeployment = false)
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
                if (":generated-" !in project.path) {
                    licenses {
                        license {
                            name = "The Apache Software License, Version 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        }
                    }
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

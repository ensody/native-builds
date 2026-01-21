@file:Suppress("UnstableApiUsage")

package com.ensody.buildlogic

import com.android.build.gradle.BaseExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Example project setup. */
class ExampleBuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}

fun Project.initBuildLogic() {
    group = "com.ensody.nativebuilds.example"

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
        if (extensions.findByType<BaseExtension>() != null) {
            setupAndroid(coreLibraryDesugaring = rootLibs.findLibrary("desugarJdkLibs").get())
        }
        if (extensions.findByType<KotlinMultiplatformExtension>() != null) {
            setupKmp {
                androidTarget()
                jvm()
                when (OS.current) {
                    OS.Linux -> {
                        linuxArm64()
                        linuxX64()
                    }

                    OS.Windows -> {
                        mingwX64()
                    }

                    OS.macOS -> {
                        linuxArm64()
                        linuxX64()
                        mingwX64()
                        allAppleMobile()
                        allDesktop()
                    }
                }

                sourceSets["jvmCommonTest"].dependencies {
                    implementation(rootLibs.findLibrary("kotlin-test-junit").get())
                    implementation(rootLibs.findLibrary("junit").get())
                }
            }
            tasks.register("testAll") {
                group = "verification"
                dependsOn("jvmTest")
                when (OS.current) {
                    OS.Linux -> {
                        when (CpuArch.current) {
                            CpuArch.aarch64 -> {
                                dependsOn(
                                    "linuxArm64Test",
                                )
                            }

                            CpuArch.x64 -> {
                                dependsOn(
                                    "linuxX64Test",
                                )
                            }
                        }
                    }

                    OS.macOS -> {
                        dependsOn(
                            "testDebugUnitTest",
                            "iosSimulatorArm64Test",
                            "iosX64Test",
                            "macosArm64Test",
                            "macosX64Test",
                        )
                    }

                    OS.Windows -> {
                        dependsOn(
                            "mingwX64Test",
                        )
                    }
                }
            }
        }
        if (extensions.findByType<KotlinBaseExtension>() != null) {
            setupKtLint(rootLibs.findLibrary("ktlint-cli").get())
        }
        if (extensions.findByType<DetektExtension>() != null) {
            setupDetekt()
        }
        block()
    }
}

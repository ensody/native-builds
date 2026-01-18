@file:Suppress("UnstableApiUsage")

package com.ensody.nativebuilds

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public fun KotlinMultiplatformExtension.cinterops(
    vararg headers: Provider<MinimalExternalModuleDependency>,
    includeHeadersPath: Boolean = true,
    block: DefaultCInteropSettings.() -> Unit,
) {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops(
                headers = headers,
                includeHeadersPath = includeHeadersPath,
                block = block,
            )
        }
    }
}

public fun KotlinNativeCompilation.cinterops(
    vararg headers: Provider<MinimalExternalModuleDependency>,
    includeHeadersPath: Boolean = true,
    block: DefaultCInteropSettings.() -> Unit,
) {
    project.addNativeBuildsHeaders(nativeBuilds = headers)
    val name = headers.first().get().name
    cinterops {
        create(name) {
            val basePath = "nativebuilds/$name"
            if (includeHeadersPath) {
                includeDirs(project.layout.buildDirectory.dir("$basePath/common"))
                includeDirs(project.layout.buildDirectory.dir("$basePath/${target.name}"))
            }
            block()
        }
    }
}

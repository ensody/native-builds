@file:Suppress("UnstableApiUsage")

package com.ensody.nativebuilds

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider

public fun Project.addNativeBuildsHeaders(
    vararg nativeBuilds: Provider<MinimalExternalModuleDependency>,
) {
    val artifacts = nativeBuilds.map { it.get() }
    val headers = artifacts.filter { it.module.name.endsWith("-headers") }
    for (artifact in artifacts) {
        var moduleName = artifact.module.name
        // Auto-inject headers if this is a lib dependency
        if (!moduleName.endsWith("-headers")) {
            moduleName = moduleName.substringBeforeLast("-") + "-headers"
            // Headers were passed explicitly, so don't attempt to auto-inject
            if (headers.any { it.module.group == artifact.module.group && it.module.name == moduleName }) {
                continue
            }
        }
        val depsSpec = "${artifact.module.group}:$moduleName:${artifact.version}"
        val dependency = project.dependencies.create(
            depsSpec,
        ) as ExternalModuleDependency
        dependency.artifact {
            extension = "zip"
            type = "zip"
        }
        dependencies.add("nativeBuild", dependency)
    }
}

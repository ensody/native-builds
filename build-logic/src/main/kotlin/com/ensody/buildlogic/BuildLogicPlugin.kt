@file:Suppress("UnstableApiUsage")

package com.ensody.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Base setup. */
class BaseBuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) {}
}

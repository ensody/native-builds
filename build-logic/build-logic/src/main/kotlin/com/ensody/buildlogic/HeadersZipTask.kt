package com.ensody.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register
import java.io.File

fun Project.registerZipTask(libProjectName: String, path: File): TaskProvider<Zip> {
    val mainZipTask = tasks.findByName("zipNativeBuilds") ?: tasks.register("zipNativeBuilds").get()
    return tasks.register<Zip>("zip-$libProjectName") {
        archiveFileName.set("$libProjectName.zip")
        destinationDirectory.set(layout.buildDirectory.dir("nativebuilds-artifacts"))
        from(path)
    }.also { mainZipTask.dependsOn(it) }
}

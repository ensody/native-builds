pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        if (System.getenv("RUNNING_ON_CI") != "true") {
            mavenLocal()
        }
    }
}

rootProject.name = "NativeBuilds"

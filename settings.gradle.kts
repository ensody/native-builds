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

val ignorePaths = mutableSetOf("build", "docs", "gradle", "src", "vcpkg", "vcpkg-tool", "vcpkg_installed")
if (System.getenv("WITH_WRAPPERS") != "true") {
    ignorePaths.add("generated-kotlin-wrappers")
}
if (System.getenv("PUBLISHING") == "true" || System.getenv("WITH_WRAPPERS") == "true") {
    ignorePaths.add("nativebuilds-loader")
    ignorePaths.add("nativebuilds-gradle-plugin")
}
fun autoDetectModules(root: File) {
    for (file in root.listFiles()) {
        if (file.name.startsWith(".") || file.name in ignorePaths) {
            continue
        }
        if (file.isDirectory) {
            val children = file.list()
            if ("settings.gradle.kts" in children) continue
            if (children.any { it == "build.gradle.kts" }) {
                include(":" + file.relativeTo(rootDir).path.replace("/", ":").replace("\\", ":"))
            } else {
                autoDetectModules(file)
            }
        }
    }
}
autoDetectModules(rootDir)

includeBuild("example")

plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    api(project(":build-logic-base"))
    api(libs.serialization.json)
    api(libs.coroutines.core)
    api(libs.ktor.client.okhttp)
}

val autoDetectPluginRegex = Regex("""^(?:public\s+)?class\s+(\w+)BuildLogicPlugin\s*:.*$""", RegexOption.MULTILINE)
val autoDetectedPlugins = file("src").walkBottomUp().filter { it.extension == "kt" }.flatMap { file ->
    autoDetectPluginRegex.findAll(file.readText()).map { it.groupValues[1] }
}.toList()

gradlePlugin {
    plugins {
        autoDetectedPlugins.forEach {  variant ->
            create("com.ensody.build-logic.${variant.lowercase()}") {
                id = name
                implementationClass = "com.ensody.buildlogic.${variant}BuildLogicPlugin"
            }
        }
    }
}

import com.ensody.buildlogic.setupBuildLogic

plugins {
    `kotlin-dsl`
    id("com.ensody.build-logic.gradle")
    id("com.ensody.build-logic.publish")
}

setupBuildLogic {
    dependencies {
        compileOnly(libs.gradle.kotlin)
        compileOnly(libs.gradle.android.api)
        implementation(libs.serialization.json)
    }

    gradlePlugin {
        plugins {
            create("com.ensody.nativebuilds") {
                id = name
                implementationClass = "com.ensody.nativebuilds.NativeBuildsPlugin"
            }
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }
}

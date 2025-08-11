import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("maven-publish")
    id("com.vanniktech.maven.publish")
}

version = System.getenv("OVERRIDE_VERSION")?.removePrefix("v")?.removePrefix("-")?.takeIf { it.isNotBlank() } ?: "0.1.0"
group = "com.ensody.nativebuilds"

dependencies {
    compileOnly(libs.gradle.kotlin)
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

configure<MavenPublishBaseExtension> {
    configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = System.getenv("RUNNING_ON_CI") == "true")
    publishToMavenCentral(automaticRelease = true)
    if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")?.isNotBlank() == true) {
        signAllPublications()
    }
    pom {
        name = "NativeBuilds Gradle plugin"
        description = project.description?.takeIf { it.isNotBlank() }
            ?: "Gradle plugin for integrating headers of native libs from the NativeBuilds project"
        url = "https://github.com/ensody/native-builds"
        scm {
            url.set(this@pom.url)
        }
        licenses {
            license {
                name = "The Apache Software License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
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
configure<PublishingExtension> {
    repositories {
        maven {
            name = "localMaven"
            val outputDir = File(rootDir, "build/localmaven")
            url = outputDir.toURI()
        }
    }
}

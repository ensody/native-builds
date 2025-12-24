package com.ensody.nativebuilds

import org.gradle.api.Project

/**
 * Substitute NativeBuild and --nativelib Android JNI artifacts with their JVM counterpart in unit tests.
 *
 * This makes it possible to run Android unit tests that utilize JNI libs.
 */
public fun Project.substituteAndroidNativeLibsInUnitTests() {
    // In Android unit tests (e.g. the KMP androidUnitTest sourceSet) we can't load Android JNI libs because the tests
    // run on the host system (Linux, macOS, Windows) instead of an Android emulator.
    // Normally, KMP Android dependencies only contain the embedded native libraries targeting Android hosts.
    // In order to run unit tests with native libs for the host we have to substitute all JNI-using dependencies.
    // The following rule is primarily meant to make all NativeBuilds work automatically:
    // See the project site for more information: https://github.com/ensody/native-builds
    configurations.all {
        // Only apply substitution to Android unit tests
        if (name.endsWith("UnitTestRuntimeClasspath")) {
            resolutionStrategy.eachDependency {
                // The NativeBuilds can all safely be substituted. They contain only the native shared libraries, but
                // no JNI code.
                if (requested.group == "com.ensody.nativebuilds" && requested.name.endsWith("-android") ||
                    // The NativeBuilds libs always get integrated in some JNI wrapper module that contains the actual
                    // JNI code. By convention we call these wrapper modules "...--native-wrapper" and the final
                    // KMP Android artifact gets published to Maven with an additional "-android" suffix.
                    // This rule should result in a relatively low chance of false positives.
                    requested.name.endsWith("--nativelib-android")
                ) {
                    // Replace with the -jvm artifact
                    useTarget("${requested.group}:${requested.name.removeSuffix("-android")}-jvm:${requested.version}")
                }
            }
        }
    }
}

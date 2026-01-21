import com.ensody.nativebuilds.jniNativeBuild
import com.ensody.buildlogic.setupBuildLogic
import com.ensody.nativebuilds.cinterops
import com.ensody.nativebuilds.substituteAndroidNativeLibsInUnitTests

plugins {
    id("com.ensody.build-logic.example")
    id("com.ensody.build-logic.android")
    id("com.ensody.build-logic.kmp")
    alias(libs.plugins.nativebuilds)
}

setupBuildLogic {
    kotlin {
        sourceSets.commonMain.dependencies {
            api(libs.nativebuilds.zstd.libzstd)
        }

        cinterops(libs.nativebuilds.zstd.headers) {
            definitionFile.set(file("src/nativeMain/cinterop/lib.def"))
        }
    }

    jniNativeBuild(
        name = "libzstd-jni",
        nativeBuilds = listOf(
            libs.nativebuilds.zstd.headers,
            libs.nativebuilds.zstd.libzstd,
        ),
    ) {
        inputFiles.from("src/jvmCommonMain/jni")
    }

    substituteAndroidNativeLibsInUnitTests()
}

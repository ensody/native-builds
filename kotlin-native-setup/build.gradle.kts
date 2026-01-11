import com.ensody.buildlogic.OS
import com.ensody.buildlogic.allDesktop
import com.ensody.buildlogic.setupBuildLogic

plugins {
    id("com.ensody.build-logic.kmp")
}

// This module only exists to download Kotlin Native's built-in cross-compilation toolchains.
// There is no public KGP API for downloading specific toolchains, so we need this module as a workaround.
setupBuildLogic {
    kotlin {
        when (OS.current) {
            OS.Linux -> {
                linuxArm64()
                linuxX64()
            }
            OS.Windows -> {
                mingwX64()
            }
            OS.macOS -> {
                allDesktop()
            }
        }
    }
}

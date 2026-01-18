package com.ensody.nativebuilds

import org.jetbrains.kotlin.konan.target.KonanTarget

public sealed interface JniTarget {
    public data object Android : JniTarget

    public sealed interface Desktop : JniTarget {
        public val konanTarget: KonanTarget
        public val zigTargetName: String

        public enum class Linux(override val konanTarget: KonanTarget, override val zigTargetName: String) : Desktop {
            ARM64(KonanTarget.LINUX_ARM64, "aarch64-linux-gnu.2.25"),
            X64(KonanTarget.LINUX_X64, "x86_64-linux-gnu.2.19"),
        }

        public enum class MacOS(override val konanTarget: KonanTarget, override val zigTargetName: String) : Desktop {
            ARM64(KonanTarget.MACOS_ARM64, "aarch64-macos-none"),
            X64(KonanTarget.MACOS_X64, "x86_64-macos-none"),
        }
        public enum class Windows(override val konanTarget: KonanTarget, override val zigTargetName: String) : Desktop {
            X64(KonanTarget.MINGW_X64, "x86_64-windows-gnu"),
        }

        public companion object {
            public val entries: List<Desktop> by lazy {
                Linux.entries + MacOS.entries + Windows.entries
            }
        }
    }

    public companion object {
        public val entries: List<JniTarget> by lazy {
            Desktop.entries + Android
        }
    }
}

public fun KonanTarget.getSourceSetName(): String =
    name.split("_").run {
        first() + drop(1).joinToString("") { it.replaceFirstChar { it.uppercase() } }
    }

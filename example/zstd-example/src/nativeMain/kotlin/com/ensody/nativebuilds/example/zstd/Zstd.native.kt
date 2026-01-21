package com.ensody.nativebuilds.example.zstd

import com.ensody.nativebuilds.example.zstd.internal.ZSTD_versionString
import kotlinx.cinterop.toKString

public actual fun getZstdVersion(): String =
    checkNotNull(ZSTD_versionString()).toKString()

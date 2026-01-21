package com.ensody.nativebuilds.example.zstd

public actual fun getZstdVersion(): String =
    ZstdWrapper.getZstdVersion()

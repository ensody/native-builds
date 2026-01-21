package com.ensody.nativebuilds.example.zstd

import kotlin.test.Test
import kotlin.test.assertEquals

internal class ZstdTest {
    @Test
    fun testVersion() {
        assertEquals("1.5.7", getZstdVersion())
    }
}

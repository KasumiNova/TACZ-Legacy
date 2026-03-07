package com.tacz.legacy.util

import org.junit.Assert.*
import org.junit.Test

class ColorHexTest {

    @Test
    fun `valid hex color parsed`() {
        assertEquals(0xFF0000, ColorHex.colorTextToRgbInt("#FF0000"))
        assertEquals(0x00FF00, ColorHex.colorTextToRgbInt("#00FF00"))
        assertEquals(0x0000FF, ColorHex.colorTextToRgbInt("#0000FF"))
        assertEquals(0xFFFFFF, ColorHex.colorTextToRgbInt("#FFFFFF"))
        assertEquals(0x000000, ColorHex.colorTextToRgbInt("#000000"))
    }

    @Test
    fun `case insensitive parsing`() {
        assertEquals(0xABCDEF, ColorHex.colorTextToRgbInt("#abcdef"))
        assertEquals(0xABCDEF, ColorHex.colorTextToRgbInt("#ABCDEF"))
        assertEquals(0xABCDEF, ColorHex.colorTextToRgbInt("#AbCdEf"))
    }

    @Test
    fun `invalid color returns fallback`() {
        assertEquals(0xFFFFFF, ColorHex.colorTextToRgbInt(""))
        assertEquals(0xFFFFFF, ColorHex.colorTextToRgbInt("FF0000"))
        assertEquals(0xFFFFFF, ColorHex.colorTextToRgbInt("#GG0000"))
        assertEquals(0xFFFFFF, ColorHex.colorTextToRgbInt("#FF00"))
        assertEquals(0xFFFFFF, ColorHex.colorTextToRgbInt("#FF000000"))
    }
}

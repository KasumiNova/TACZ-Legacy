package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

public class PacketWeaponWorkbenchOpenScreenTest {

    @Test
    public fun `codec should preserve workbench open payload`() {
        val source = PacketWeaponWorkbenchOpenScreen(
            gunId = "timeless50",
            blockX = 128,
            blockY = 64,
            blockZ = -32
        )
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponWorkbenchOpenScreen()
        decoded.fromBytes(buf)

        assertEquals("timeless50", decoded.gunId)
        assertEquals(128, decoded.blockX)
        assertEquals(64, decoded.blockY)
        assertEquals(-32, decoded.blockZ)
        assertFalse(buf.isReadable)
    }
}

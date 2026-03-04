package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertFalse
import org.junit.Test

public class PacketWeaponWorkbenchRefreshScreenTest {

    @Test
    public fun `codec should preserve empty payload`() {
        val source = PacketWeaponWorkbenchRefreshScreen()
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponWorkbenchRefreshScreen()
        decoded.fromBytes(buf)

        assertFalse(buf.isReadable)
    }
}

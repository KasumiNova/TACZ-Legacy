package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

public class PacketWeaponWorkbenchSessionControlTest {

    @Test
    public fun `codec should preserve close action payload`() {
        val source = PacketWeaponWorkbenchSessionControl.close()
        val expectedActionCode = source.actionCode
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponWorkbenchSessionControl()
        decoded.fromBytes(buf)

        assertEquals(expectedActionCode, decoded.actionCode)
        assertFalse(buf.isReadable)
    }
}

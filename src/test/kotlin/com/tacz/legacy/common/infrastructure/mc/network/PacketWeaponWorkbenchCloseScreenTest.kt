package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

public class PacketWeaponWorkbenchCloseScreenTest {

    @Test
    public fun `codec should preserve close reason payload`() {
        val source = PacketWeaponWorkbenchCloseScreen("改枪台会话已失效：距离工作台过远")
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponWorkbenchCloseScreen()
        decoded.fromBytes(buf)

        assertEquals("改枪台会话已失效：距离工作台过远", decoded.reasonMessage)
        assertFalse(buf.isReadable)
    }
}

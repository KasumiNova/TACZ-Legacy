package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Test

public class PacketWeaponBaseTimestampAckTest {

    @Test
    public fun `codec should support empty payload`() {
        val source = PacketWeaponBaseTimestampAck()
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponBaseTimestampAck()
        decoded.fromBytes(buf)

        assertEquals(0, buf.readableBytes())
    }
}

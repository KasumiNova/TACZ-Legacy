package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Test

public class PacketWeaponBaseTimestampSyncTest {

    @Test
    public fun `codec should support empty payload`() {
        val source = PacketWeaponBaseTimestampSync()
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponBaseTimestampSync()
        decoded.fromBytes(buf)

        assertEquals(0, buf.readableBytes())
    }
}

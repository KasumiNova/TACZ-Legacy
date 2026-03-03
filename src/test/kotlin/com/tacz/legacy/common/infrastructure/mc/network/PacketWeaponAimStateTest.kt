package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class PacketWeaponAimStateTest {

    @Test
    public fun `codec should preserve aiming flag`() {
        val source = PacketWeaponAimState(isAiming = true)
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponAimState()
        decoded.fromBytes(buf)

        assertTrue(decoded.isAiming)
        assertFalse(buf.isReadable)
    }
}

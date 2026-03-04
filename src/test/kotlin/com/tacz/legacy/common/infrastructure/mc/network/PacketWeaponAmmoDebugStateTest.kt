package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

public class PacketWeaponAmmoDebugStateTest {

    @Test
    public fun `codec should preserve ammo debug payload`() {
        val source = PacketWeaponAmmoDebugState(
            ammoInMagazine = 27,
            ammoReserve = 135
        )
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponAmmoDebugState()
        decoded.fromBytes(buf)

        assertEquals(27, decoded.ammoInMagazine)
        assertEquals(135, decoded.ammoReserve)
        assertFalse(buf.isReadable)
    }
}

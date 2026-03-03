package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Test

public class PacketWeaponSessionClearTest {

    @Test
    public fun `codec should round trip session id`() {
        val source = PacketWeaponSessionClear(
            sessionId = "player:abc",
            ackSequenceId = 11,
            correctionReason = WeaponSessionCorrectionReason.NO_SESSION
        )
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponSessionClear()
        decoded.fromBytes(buf)

        assertEquals("player:abc", decoded.sessionId)
        assertEquals(11, decoded.ackSequenceId)
        assertEquals(WeaponSessionCorrectionReason.NO_SESSION.code, decoded.correctionReasonCode)
    }

}

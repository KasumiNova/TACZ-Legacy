package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class PacketWeaponSessionSyncTest {

    @Test
    public fun `fromDebugSnapshot and codec should round trip session snapshot`() {
        val source = PacketWeaponSessionSync.fromDebugSnapshot(
            sessionId = "player:abc",
            debugSnapshot = WeaponSessionDebugSnapshot(
                sourceId = "sample_pack/data/tacz/data/guns/rifle/ak47.json",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 27,
                    ammoReserve = 90,
                    isTriggerHeld = true,
                    reloadTicksRemaining = 0,
                    cooldownTicksRemaining = 2,
                    semiLocked = false,
                    burstShotsRemaining = 0,
                    totalShotsFired = 11
                )
            ),
            ackSequenceId = 25,
            correctionReason = WeaponSessionCorrectionReason.INPUT_ACCEPTED,
            syncedAtEpochMillis = 123456789L
        )

        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponSessionSync()
        decoded.fromBytes(buf)
        val snapshot = decoded.toSnapshotOrNull()

        assertNotNull(snapshot)
        requireNotNull(snapshot)
        assertEquals("player:abc", snapshot.sessionId)
        assertEquals("sample_pack/data/tacz/data/guns/rifle/ak47.json", snapshot.sourceId)
        assertEquals("ak47", snapshot.gunId)
        assertEquals(WeaponState.FIRING, snapshot.snapshot.state)
        assertEquals(27, snapshot.snapshot.ammoInMagazine)
        assertEquals(90, snapshot.snapshot.ammoReserve)
        assertEquals(true, snapshot.snapshot.isTriggerHeld)
        assertEquals(0, snapshot.snapshot.reloadTicksRemaining)
        assertEquals(2, snapshot.snapshot.cooldownTicksRemaining)
        assertEquals(false, snapshot.snapshot.semiLocked)
        assertEquals(0, snapshot.snapshot.burstShotsRemaining)
        assertEquals(11, snapshot.snapshot.totalShotsFired)
        assertEquals(25, snapshot.ackSequenceId)
        assertEquals(WeaponSessionCorrectionReason.INPUT_ACCEPTED, snapshot.correctionReason)
        assertEquals(123456789L, snapshot.syncedAtEpochMillis)
    }

    @Test
    public fun `toSnapshotOrNull should reject invalid state ordinal`() {
        val packet = PacketWeaponSessionSync().apply {
            sessionId = "player:abc"
            sourceId = "src"
            gunId = "ak47"
            stateOrdinal = 127
            ammoInMagazine = 1
            ammoReserve = 2
        }

        assertNull(packet.toSnapshotOrNull())
    }
}

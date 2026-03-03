package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponSessionSyncClientRegistryTest {

    @Test
    public fun `upsert snapshot should also persist receipt metadata`() {
        WeaponSessionSyncClientRegistry.clear()

        WeaponSessionSyncClientRegistry.upsert(
            WeaponSessionSyncSnapshot(
                sessionId = "player:abc",
                sourceId = "src",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 29,
                    ammoReserve = 90,
                    cooldownTicksRemaining = 2
                ),
                ackSequenceId = 7,
                correctionReason = WeaponSessionCorrectionReason.INPUT_ACCEPTED,
                syncedAtEpochMillis = 1000L
            )
        )

        val receipt = WeaponSessionSyncClientRegistry.receipt("player:abc")
        assertNotNull(receipt)
        assertEquals(7, receipt?.ackSequenceId)
        assertEquals(WeaponSessionCorrectionReason.INPUT_ACCEPTED, receipt?.correctionReason)
        assertEquals(1000L, receipt?.syncedAtEpochMillis)
    }

    @Test
    public fun `upsertReceipt without snapshot should be queryable and removable`() {
        WeaponSessionSyncClientRegistry.clear()

        WeaponSessionSyncClientRegistry.upsertReceipt(
            sessionId = "player:abc",
            ackSequenceId = 13,
            correctionReason = WeaponSessionCorrectionReason.NO_SESSION,
            syncedAtEpochMillis = 2000L
        )

        assertNull(WeaponSessionSyncClientRegistry.get("player:abc"))
        val receipt = WeaponSessionSyncClientRegistry.receipt("player:abc")
        assertNotNull(receipt)
        assertEquals(13, receipt?.ackSequenceId)
        assertEquals(WeaponSessionCorrectionReason.NO_SESSION, receipt?.correctionReason)

        WeaponSessionSyncClientRegistry.remove("player:abc")
        assertNull(WeaponSessionSyncClientRegistry.get("player:abc"))
        assertNull(WeaponSessionSyncClientRegistry.receipt("player:abc"))
    }
}

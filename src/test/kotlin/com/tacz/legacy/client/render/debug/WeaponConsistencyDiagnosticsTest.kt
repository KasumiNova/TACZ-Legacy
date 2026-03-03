package com.tacz.legacy.client.render.debug

import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionCorrectionReason
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEvent
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEventType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

public class WeaponConsistencyDiagnosticsTest {

    @Test
    public fun `animation observation should count transitions and shell events once`() {
        WeaponConsistencyDiagnostics.clearAll()
        val sessionId = "player:test:client"

        WeaponConsistencyDiagnostics.observeAnimationSnapshot(
            sessionId = sessionId,
            snapshot = snapshot(
                clip = WeaponAnimationClipType.FIRE,
                transientEvents = listOf(
                    event(sequence = 1L, clip = WeaponAnimationClipType.FIRE)
                )
            )
        )
        WeaponConsistencyDiagnostics.observeAnimationSnapshot(
            sessionId = sessionId,
            snapshot = snapshot(
                clip = WeaponAnimationClipType.BOLT,
                transientEvents = listOf(
                    event(sequence = 1L, clip = WeaponAnimationClipType.FIRE),
                    event(sequence = 2L, clip = WeaponAnimationClipType.BOLT)
                )
            )
        )

        val diagnostics = WeaponConsistencyDiagnostics.snapshot(sessionId)
        assertNotNull(diagnostics)
        assertEquals(1, diagnostics?.transitionTotal)
        assertEquals("FIRE->BOLT", diagnostics?.lastTransition)
        assertEquals(2, diagnostics?.shellEventsObserved)
    }

    @Test
    public fun `shell spawn counters should distinguish perspective and source`() {
        WeaponConsistencyDiagnostics.clearAll()
        val sessionId = "player:test:client"

        WeaponConsistencyDiagnostics.recordShellSpawn(
            sessionId = sessionId,
            perspective = ShellSpawnPerspective.FIRST_PERSON,
            source = ShellSpawnSource.EVENT,
            count = 2
        )
        WeaponConsistencyDiagnostics.recordShellSpawn(
            sessionId = sessionId,
            perspective = ShellSpawnPerspective.FIRST_PERSON,
            source = ShellSpawnSource.FALLBACK,
            count = 1
        )
        WeaponConsistencyDiagnostics.recordShellSpawn(
            sessionId = sessionId,
            perspective = ShellSpawnPerspective.THIRD_PERSON,
            source = ShellSpawnSource.FALLBACK,
            count = 3
        )

        val diagnostics = WeaponConsistencyDiagnostics.snapshot(sessionId)
        assertNotNull(diagnostics)
        assertEquals(2, diagnostics?.firstPersonEventSpawnCount)
        assertEquals(1, diagnostics?.firstPersonFallbackSpawnCount)
        assertEquals(0, diagnostics?.thirdPersonEventSpawnCount)
        assertEquals(3, diagnostics?.thirdPersonFallbackSpawnCount)
    }

    @Test
    public fun `drift diagnostics should accumulate samples and max mismatch`() {
        WeaponConsistencyDiagnostics.clearAll()
        val sessionId = "player:test:client"

        WeaponConsistencyDiagnostics.recordSessionDrift(
            sessionId = sessionId,
            driftFields = 0,
            correctionReason = WeaponSessionCorrectionReason.PERIODIC,
            nowMillis = 1_000L
        )
        WeaponConsistencyDiagnostics.recordSessionDrift(
            sessionId = sessionId,
            driftFields = 2,
            correctionReason = WeaponSessionCorrectionReason.INPUT_REJECTED,
            nowMillis = 2_100L
        )
        WeaponConsistencyDiagnostics.recordSessionDrift(
            sessionId = sessionId,
            driftFields = 1,
            correctionReason = WeaponSessionCorrectionReason.INPUT_REJECTED,
            nowMillis = 3_200L
        )

        val diagnostics = WeaponConsistencyDiagnostics.snapshot(sessionId)
        assertNotNull(diagnostics)
        assertEquals(3, diagnostics?.driftSampleCount)
        assertEquals(2, diagnostics?.driftDetectedCount)
        assertEquals(1, diagnostics?.driftLastFields)
        assertEquals(2, diagnostics?.driftMaxFields)
        assertEquals(2, diagnostics?.correctionReasonCounts?.get(WeaponSessionCorrectionReason.INPUT_REJECTED))
    }

    @Test
    public fun `snapshot drift field counter should return mismatch count`() {
        val local = WeaponSnapshot(
            state = com.tacz.legacy.common.domain.weapon.WeaponState.IDLE,
            ammoInMagazine = 30,
            ammoReserve = 90,
            isTriggerHeld = false,
            reloadTicksRemaining = 0,
            cooldownTicksRemaining = 0,
            semiLocked = false,
            burstShotsRemaining = 0,
            totalShotsFired = 12
        )
        val authoritative = local.copy(
            ammoInMagazine = 28,
            cooldownTicksRemaining = 2,
            totalShotsFired = 13
        )

        val mismatch = WeaponConsistencyDiagnostics.countSnapshotDriftFields(
            local = local,
            authoritative = authoritative
        )

        assertEquals(3, mismatch)
    }

    private fun snapshot(
        clip: WeaponAnimationClipType,
        transientEvents: List<WeaponAnimationRuntimeEvent>
    ): WeaponAnimationRuntimeSnapshot = WeaponAnimationRuntimeSnapshot(
        sessionId = "player:test:client",
        gunId = "ak47",
        clip = clip,
        progress = 0f,
        elapsedMillis = 0L,
        durationMillis = 100L,
        lastUpdatedAtMillis = 0L,
        transientEvents = transientEvents
    )

    private fun event(sequence: Long, clip: WeaponAnimationClipType): WeaponAnimationRuntimeEvent =
        WeaponAnimationRuntimeEvent(
            sequence = sequence,
            type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
            clip = clip,
            emittedAtMillis = 0L
        )
}

package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.weapon.WeaponAnimationSignal
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.domain.weapon.WeaponStepResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponAnimationRuntimeRegistryTest {

    @Test
    public fun `observe fire should enter fire clip then return idle after duration`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 29,
                    ammoReserve = 60,
                    cooldownTicksRemaining = 1,
                    totalShotsFired = 1
                ),
                shotFired = true,
                signals = setOf(WeaponAnimationSignal.FIRE)
            ),
            nowMillis = 1_000L
        )

        val started = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 1_000L)
        assertNotNull(started)
        assertEquals(WeaponAnimationClipType.FIRE, started?.clip)
        assertEquals(0f, started?.progress ?: -1f, 0.0001f)

        val mid = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 1_060L)
        assertNotNull(mid)
        assertTrue((mid?.progress ?: 0f) > 0f)

        val expired = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 1_140L)
        assertNotNull(expired)
        assertEquals(WeaponAnimationClipType.IDLE, expired?.clip)
        assertEquals(0f, expired?.progress ?: -1f, 0.0001f)
    }

    @Test
    public fun `observe reload should track progress by reload ticks and settle idle on complete`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.RELOADING,
                    ammoInMagazine = 0,
                    ammoReserve = 90,
                    reloadTicksRemaining = 30
                ),
                reloadStarted = true,
                signals = setOf(WeaponAnimationSignal.RELOAD_START)
            ),
            reloadTicks = 40,
            nowMillis = 2_000L
        )

        val start = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 2_000L)
        assertNotNull(start)
        assertEquals(WeaponAnimationClipType.RELOAD, start?.clip)
        assertEquals(0.25f, start?.progress ?: -1f, 0.0001f)

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.RELOADING,
                    ammoInMagazine = 0,
                    ammoReserve = 90,
                    reloadTicksRemaining = 10
                )
            ),
            reloadTicks = 40,
            nowMillis = 2_500L
        )

        val mid = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 2_500L)
        assertNotNull(mid)
        assertEquals(WeaponAnimationClipType.RELOAD, mid?.clip)
        assertEquals(0.75f, mid?.progress ?: -1f, 0.0001f)

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 30,
                    ammoReserve = 60,
                    reloadTicksRemaining = 0
                ),
                reloadCompleted = true,
                signals = setOf(WeaponAnimationSignal.RELOAD_COMPLETE)
            ),
            reloadTicks = 40,
            nowMillis = 3_000L
        )

        val completed = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 3_000L)
        assertNotNull(completed)
        assertEquals(WeaponAnimationClipType.IDLE, completed?.clip)
        assertEquals(0f, completed?.progress ?: -1f, 0.0001f)
    }

    @Test
    public fun `remove session should clear tracked animation state`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 30,
                    ammoReserve = 90
                ),
                signals = setOf(WeaponAnimationSignal.INSPECT)
            ),
            nowMillis = 4_000L
        )

        assertNotNull(WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 4_010L))
        WeaponAnimationRuntimeRegistry.removeSession("player:test")
        assertNull(WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 4_020L))
    }

    @Test
    public fun `observe inspect should use provided clip duration override`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 30,
                    ammoReserve = 90
                ),
                signals = setOf(WeaponAnimationSignal.INSPECT)
            ),
            clipDurationOverridesMillis = mapOf(
                WeaponAnimationClipType.INSPECT to 2_000L
            ),
            nowMillis = 5_000L
        )

        val mid = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 6_000L)
        assertNotNull(mid)
        assertEquals(WeaponAnimationClipType.INSPECT, mid?.clip)
        assertEquals(0.5f, mid?.progress ?: -1f, 0.0001f)

        val done = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 7_001L)
        assertNotNull(done)
        assertEquals(WeaponAnimationClipType.IDLE, done?.clip)
    }

    @Test
    public fun `manual bolt preference should transition fire to bolt before settling idle`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "m870",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 7,
                    ammoReserve = 24,
                    cooldownTicksRemaining = 1,
                    totalShotsFired = 1
                ),
                shotFired = true,
                signals = setOf(WeaponAnimationSignal.FIRE)
            ),
            clipDurationOverridesMillis = mapOf(
                WeaponAnimationClipType.FIRE to 100L,
                WeaponAnimationClipType.BOLT to 250L
            ),
            preferBoltCycleAfterFire = true,
            nowMillis = 10_000L
        )

        val fireClip = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 10_120L)
        assertNotNull(fireClip)
        assertEquals(WeaponAnimationClipType.BOLT, fireClip?.clip)

        val boltMid = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 10_240L)
        assertNotNull(boltMid)
        assertEquals(WeaponAnimationClipType.BOLT, boltMid?.clip)
        assertTrue((boltMid?.progress ?: 0f) > 0f)

        val done = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 10_380L)
        assertNotNull(done)
        assertEquals(WeaponAnimationClipType.IDLE, done?.clip)
    }

    @Test
    public fun `fire clip should emit shell eject transient event by default`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 29,
                    ammoReserve = 60,
                    cooldownTicksRemaining = 1,
                    totalShotsFired = 1
                ),
                shotFired = true,
                signals = setOf(WeaponAnimationSignal.FIRE)
            ),
            nowMillis = 11_000L
        )

        val started = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 11_000L)
        val event = started?.transientEvents?.lastOrNull()
        assertNotNull(event)
        assertEquals(WeaponAnimationRuntimeEventType.SHELL_EJECT, event?.type)
        assertEquals(WeaponAnimationClipType.FIRE, event?.clip)
    }

    @Test
    public fun `reload shell event should fire after configured trigger millis`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "ak47",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.RELOADING,
                    ammoInMagazine = 0,
                    ammoReserve = 90,
                    reloadTicksRemaining = 30
                ),
                reloadStarted = true,
                signals = setOf(WeaponAnimationSignal.RELOAD_START)
            ),
            shellEjectPlan = WeaponAnimationShellEjectPlan(
                fireTriggerMillis = null,
                reloadTriggerMillis = 400L,
                boltTriggerMillis = null
            ),
            nowMillis = 12_000L
        )

        val beforeTrigger = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 12_350L)
        assertTrue(beforeTrigger?.transientEvents.orEmpty().isEmpty())

        val afterTrigger = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 12_410L)
        val event = afterTrigger?.transientEvents?.lastOrNull()
        assertNotNull(event)
        assertEquals(WeaponAnimationRuntimeEventType.SHELL_EJECT, event?.type)
        assertEquals(WeaponAnimationClipType.RELOAD, event?.clip)
    }

    @Test
    public fun `manual bolt cycle should emit shell event on bolt trigger and suppress fire event when configured`() {
        WeaponAnimationRuntimeRegistry.clear()

        WeaponAnimationRuntimeRegistry.observeBehavior(
            sessionId = "player:test",
            gunId = "m870",
            result = behaviorResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.FIRING,
                    ammoInMagazine = 7,
                    ammoReserve = 24,
                    cooldownTicksRemaining = 1,
                    totalShotsFired = 1
                ),
                shotFired = true,
                signals = setOf(WeaponAnimationSignal.FIRE)
            ),
            clipDurationOverridesMillis = mapOf(
                WeaponAnimationClipType.FIRE to 100L,
                WeaponAnimationClipType.BOLT to 250L
            ),
            preferBoltCycleAfterFire = true,
            shellEjectPlan = WeaponAnimationShellEjectPlan(
                fireTriggerMillis = null,
                reloadTriggerMillis = null,
                boltTriggerMillis = 120L
            ),
            nowMillis = 13_000L
        )

        val afterFire = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 13_050L)
        assertTrue(afterFire?.transientEvents.orEmpty().isEmpty())

        val boltStarted = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 13_120L)
        assertEquals(WeaponAnimationClipType.BOLT, boltStarted?.clip)
        assertTrue(boltStarted?.transientEvents.orEmpty().isEmpty())

        val boltTriggered = WeaponAnimationRuntimeRegistry.snapshot("player:test", nowMillis = 13_260L)
        val event = boltTriggered?.transientEvents?.lastOrNull()
        assertNotNull(event)
        assertEquals(WeaponAnimationRuntimeEventType.SHELL_EJECT, event?.type)
        assertEquals(WeaponAnimationClipType.BOLT, event?.clip)
        assertFalse(event?.sequence == 0L)
    }

    private fun behaviorResult(
        snapshot: WeaponSnapshot,
        shotFired: Boolean = false,
        dryFired: Boolean = false,
        reloadStarted: Boolean = false,
        reloadCompleted: Boolean = false,
        signals: Set<WeaponAnimationSignal> = emptySet()
    ): WeaponBehaviorResult =
        WeaponBehaviorResult(
            step = WeaponStepResult(
                snapshot = snapshot,
                shotFired = shotFired,
                dryFired = dryFired,
                reloadStarted = reloadStarted,
                reloadCompleted = reloadCompleted
            ),
            animationSignals = signals
        )

}

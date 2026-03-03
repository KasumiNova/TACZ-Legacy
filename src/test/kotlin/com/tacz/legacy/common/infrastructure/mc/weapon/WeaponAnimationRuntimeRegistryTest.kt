package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.weapon.WeaponAnimationSignal
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.domain.weapon.WeaponStepResult
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

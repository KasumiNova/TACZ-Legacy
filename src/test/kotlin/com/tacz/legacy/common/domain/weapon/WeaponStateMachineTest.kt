package com.tacz.legacy.common.domain.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponStateMachineTest {

    @Test
    public fun `auto mode should fire on interval while trigger is held`() {
        val spec = WeaponSpec(
            magazineSize = 5,
            roundsPerMinute = 600,
            reloadTicks = 3,
            fireMode = WeaponFireMode.AUTO
        )
        val machine = WeaponStateMachine(
            spec = spec,
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 5, ammoReserve = 0)
        )

        val onPress = machine.dispatch(WeaponInput.TriggerPressed)
        assertTrue(onPress.shotFired)
        assertEquals(4, onPress.snapshot.ammoInMagazine)
        assertEquals(1, onPress.snapshot.totalShotsFired)

        val tick1 = machine.dispatch(WeaponInput.Tick)
        assertFalse(tick1.shotFired)
        assertEquals(4, tick1.snapshot.ammoInMagazine)
        assertEquals(1, tick1.snapshot.totalShotsFired)

        val tick2 = machine.dispatch(WeaponInput.Tick)
        assertTrue(tick2.shotFired)
        assertEquals(3, tick2.snapshot.ammoInMagazine)
        assertEquals(2, tick2.snapshot.totalShotsFired)
    }

    @Test
    public fun `semi mode should require trigger release before next shot`() {
        val spec = WeaponSpec(
            magazineSize = 6,
            roundsPerMinute = 1200,
            reloadTicks = 2,
            fireMode = WeaponFireMode.SEMI
        )
        val machine = WeaponStateMachine(
            spec = spec,
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 6, ammoReserve = 0)
        )

        val firstPress = machine.dispatch(WeaponInput.TriggerPressed)
        assertTrue(firstPress.shotFired)
        assertEquals(1, firstPress.snapshot.totalShotsFired)

        val secondPress = machine.dispatch(WeaponInput.TriggerPressed)
        assertFalse(secondPress.shotFired)
        assertEquals(1, secondPress.snapshot.totalShotsFired)

        machine.dispatch(WeaponInput.Tick)
        machine.dispatch(WeaponInput.TriggerReleased)

        val thirdPress = machine.dispatch(WeaponInput.TriggerPressed)
        assertTrue(thirdPress.shotFired)
        assertEquals(2, thirdPress.snapshot.totalShotsFired)
        assertEquals(4, thirdPress.snapshot.ammoInMagazine)
    }

    @Test
    public fun `reload should transition and refill magazine after reload ticks`() {
        val spec = WeaponSpec(
            magazineSize = 30,
            roundsPerMinute = 600,
            reloadTicks = 3,
            fireMode = WeaponFireMode.AUTO
        )
        val machine = WeaponStateMachine(
            spec = spec,
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 5, ammoReserve = 40)
        )

        val reloadStart = machine.dispatch(WeaponInput.ReloadPressed)
        assertTrue(reloadStart.reloadStarted)
        assertEquals(WeaponState.RELOADING, reloadStart.snapshot.state)
        assertEquals(3, reloadStart.snapshot.reloadTicksRemaining)

        machine.dispatch(WeaponInput.Tick)
        machine.dispatch(WeaponInput.Tick)
        val reloadDone = machine.dispatch(WeaponInput.Tick)

        assertTrue(reloadDone.reloadCompleted)
        assertEquals(WeaponState.IDLE, reloadDone.snapshot.state)
        assertEquals(30, reloadDone.snapshot.ammoInMagazine)
        assertEquals(15, reloadDone.snapshot.ammoReserve)
    }

    @Test
    public fun `reload should not start when magazine full or reserve empty`() {
        val spec = WeaponSpec(
            magazineSize = 30,
            roundsPerMinute = 600,
            reloadTicks = 2,
            fireMode = WeaponFireMode.AUTO
        )

        val fullMagMachine = WeaponStateMachine(
            spec = spec,
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 30, ammoReserve = 40)
        )
        val fullMagAttempt = fullMagMachine.dispatch(WeaponInput.ReloadPressed)
        assertFalse(fullMagAttempt.reloadStarted)
        assertEquals(WeaponState.IDLE, fullMagAttempt.snapshot.state)

        val noReserveMachine = WeaponStateMachine(
            spec = spec,
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 10, ammoReserve = 0)
        )
        val noReserveAttempt = noReserveMachine.dispatch(WeaponInput.ReloadPressed)
        assertFalse(noReserveAttempt.reloadStarted)
        assertEquals(WeaponState.IDLE, noReserveAttempt.snapshot.state)
    }

    @Test
    public fun `burst mode should fire configured burst chain then lock until release`() {
        val spec = WeaponSpec(
            magazineSize = 12,
            roundsPerMinute = 1200,
            reloadTicks = 2,
            fireMode = WeaponFireMode.BURST,
            burstSize = 3
        )
        val machine = WeaponStateMachine(
            spec = spec,
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 12, ammoReserve = 0)
        )

        val press = machine.dispatch(WeaponInput.TriggerPressed)
        assertTrue(press.shotFired)
        assertEquals(11, press.snapshot.ammoInMagazine)
        assertEquals(2, press.snapshot.burstShotsRemaining)
        assertFalse(press.snapshot.semiLocked)

        val tick1 = machine.dispatch(WeaponInput.Tick)
        assertTrue(tick1.shotFired)
        assertEquals(10, tick1.snapshot.ammoInMagazine)
        assertEquals(1, tick1.snapshot.burstShotsRemaining)
        assertFalse(tick1.snapshot.semiLocked)

        val tick2 = machine.dispatch(WeaponInput.Tick)
        assertTrue(tick2.shotFired)
        assertEquals(9, tick2.snapshot.ammoInMagazine)
        assertEquals(0, tick2.snapshot.burstShotsRemaining)
        assertTrue(tick2.snapshot.semiLocked)

        val stillHeld = machine.dispatch(WeaponInput.Tick)
        assertFalse(stillHeld.shotFired)
        assertEquals(9, stillHeld.snapshot.ammoInMagazine)

        machine.dispatch(WeaponInput.TriggerReleased)
        val pressAgain = machine.dispatch(WeaponInput.TriggerPressed)
        assertTrue(pressAgain.shotFired)
        assertEquals(8, pressAgain.snapshot.ammoInMagazine)
    }

}
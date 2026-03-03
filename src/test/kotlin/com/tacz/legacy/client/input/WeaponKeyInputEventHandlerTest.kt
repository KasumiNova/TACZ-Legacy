package com.tacz.legacy.client.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponKeyInputEventHandlerTest {

    private val handler = WeaponKeyInputEventHandler()

    @Test
    public fun `mouse left down with gun in game should cancel vanilla primary input`() {
        val result = handler.shouldCancelVanillaPrimaryMouseInput(
            button = 0,
            buttonState = true,
            hasScreen = false,
            holdingLegacyGun = true
        )

        assertTrue(result)
    }

    @Test
    public fun `mouse left down should not cancel when gui open or not holding gun`() {
        assertFalse(
            handler.shouldCancelVanillaPrimaryMouseInput(
                button = 0,
                buttonState = true,
                hasScreen = true,
                holdingLegacyGun = true
            )
        )

        assertFalse(
            handler.shouldCancelVanillaPrimaryMouseInput(
                button = 0,
                buttonState = true,
                hasScreen = false,
                holdingLegacyGun = false
            )
        )
    }

    @Test
    public fun `non left click or release should not cancel`() {
        assertFalse(
            handler.shouldCancelVanillaPrimaryMouseInput(
                button = 1,
                buttonState = true,
                hasScreen = false,
                holdingLegacyGun = true
            )
        )

        assertFalse(
            handler.shouldCancelVanillaPrimaryMouseInput(
                button = 0,
                buttonState = false,
                hasScreen = false,
                holdingLegacyGun = true
            )
        )
    }

    @Test
    public fun `aim intent target should require use down gun held and no swing`() {
        assertTrue(
            handler.resolveAimIntentTarget(
                hasScreen = false,
                holdingLegacyGun = true,
                useDown = true,
                isSwinging = false
            )
        )

        assertFalse(
            handler.resolveAimIntentTarget(
                hasScreen = true,
                holdingLegacyGun = true,
                useDown = true,
                isSwinging = false
            )
        )

        assertFalse(
            handler.resolveAimIntentTarget(
                hasScreen = false,
                holdingLegacyGun = false,
                useDown = true,
                isSwinging = false
            )
        )

        assertFalse(
            handler.resolveAimIntentTarget(
                hasScreen = false,
                holdingLegacyGun = true,
                useDown = false,
                isSwinging = false
            )
        )

        assertFalse(
            handler.resolveAimIntentTarget(
                hasScreen = false,
                holdingLegacyGun = true,
                useDown = true,
                isSwinging = true
            )
        )
    }

    @Test
    public fun `aim sync should send on first state and state transitions only`() {
        assertTrue(handler.shouldSyncAimingIntent(lastSynced = null, current = false))
        assertFalse(handler.shouldSyncAimingIntent(lastSynced = false, current = false))
        assertTrue(handler.shouldSyncAimingIntent(lastSynced = false, current = true))
        assertFalse(handler.shouldSyncAimingIntent(lastSynced = true, current = true))
    }
}

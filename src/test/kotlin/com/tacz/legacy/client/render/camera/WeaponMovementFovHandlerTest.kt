package com.tacz.legacy.client.render.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponMovementFovHandlerTest {

    private val handler: WeaponMovementFovHandler = WeaponMovementFovHandler()

    @Test
    public fun `suppression decision should require config enabled and gun in hand`() {
        assertTrue(handler.shouldSuppressMovementFov(configEnabled = true, holdingLegacyGun = true))
        assertFalse(handler.shouldSuppressMovementFov(configEnabled = false, holdingLegacyGun = true))
        assertFalse(handler.shouldSuppressMovementFov(configEnabled = true, holdingLegacyGun = false))
        assertFalse(handler.shouldSuppressMovementFov(configEnabled = false, holdingLegacyGun = false))
    }

    @Test
    public fun `base gun fov modifier should preserve only flying and sprinting factors`() {
        val idle = handler.resolveBaseGunFovModifier(isFlying = false, isSprinting = false)
        val flying = handler.resolveBaseGunFovModifier(isFlying = true, isSprinting = false)
        val sprinting = handler.resolveBaseGunFovModifier(isFlying = false, isSprinting = true)
        val both = handler.resolveBaseGunFovModifier(isFlying = true, isSprinting = true)

        assertEquals(1.0f, idle, 1e-6f)
        assertEquals(1.1f, flying, 1e-6f)
        assertEquals(1.15f, sprinting, 1e-6f)
        assertEquals(1.265f, both, 1e-6f)
    }
}

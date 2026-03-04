package com.tacz.legacy.client.gui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

public class WeaponGunsmithImmersiveRuntimeTest {

    @Test
    public fun `toggle should enter and exit immersive mode for same gun`() {
        WeaponGunsmithImmersiveRuntime.resetForTests()

        val entered = WeaponGunsmithImmersiveRuntime.toggle("ak47")
        val exited = WeaponGunsmithImmersiveRuntime.toggle("ak47")

        assertTrue(entered.enabled)
        assertFalse(exited.enabled)
    }

    @Test
    public fun `deactivateIfGunChanged should close immersive mode when gun switched`() {
        WeaponGunsmithImmersiveRuntime.resetForTests()
        WeaponGunsmithImmersiveRuntime.toggle("ak47")

        WeaponGunsmithImmersiveRuntime.deactivateIfGunChanged("m4a1")

        assertFalse(WeaponGunsmithImmersiveRuntime.snapshot().enabled)
    }

    @Test
    public fun `isActiveForGun should only match current immersive gun`() {
        WeaponGunsmithImmersiveRuntime.resetForTests()
        WeaponGunsmithImmersiveRuntime.toggle("ak47")

        assertTrue(WeaponGunsmithImmersiveRuntime.isActiveForGun("ak47"))
        assertFalse(WeaponGunsmithImmersiveRuntime.isActiveForGun("m4a1"))
    }

    @Test
    public fun `activate should deterministically switch active gun`() {
        WeaponGunsmithImmersiveRuntime.resetForTests()
        WeaponGunsmithImmersiveRuntime.activate("ak47")
        WeaponGunsmithImmersiveRuntime.activate("m4a1")

        val snapshot = WeaponGunsmithImmersiveRuntime.snapshot()
        assertTrue(snapshot.enabled)
        assertEquals("m4a1", snapshot.gunId)
    }
}

package com.tacz.legacy.common.infrastructure.mc.gunpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class GunPackReloadCoordinatorTest {

    @Test
    public fun `compute item registry delta should be empty when sets are equal`() {
        val locked = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.m4a1")
        val current = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.m4a1")

        val delta = GunPackReloadCoordinator.computeItemRegistryDelta(locked, current)

        assertFalse(delta.changed)
        assertTrue(delta.addedPaths.isEmpty())
        assertTrue(delta.removedPaths.isEmpty())
    }

    @Test
    public fun `compute item registry delta should report added and removed entries`() {
        val locked = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.m4a1")
        val current = setOf("tacz.dynamic.gun.ak47", "tacz.dynamic.gun.hk416")

        val delta = GunPackReloadCoordinator.computeItemRegistryDelta(locked, current)

        assertTrue(delta.changed)
        assertEquals(setOf("tacz.dynamic.gun.hk416"), delta.addedPaths)
        assertEquals(setOf("tacz.dynamic.gun.m4a1"), delta.removedPaths)
    }
}

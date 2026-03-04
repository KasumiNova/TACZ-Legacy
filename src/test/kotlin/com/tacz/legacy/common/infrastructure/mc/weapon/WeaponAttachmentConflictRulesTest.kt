package com.tacz.legacy.common.infrastructure.mc.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponAttachmentConflictRulesTest {

    @Test
    public fun `validateInstall should reject when slot already occupied`() {
        val snapshot = WeaponAttachmentSnapshot(scopeId = "tacz:scope_red_dot")

        val check = WeaponAttachmentConflictRules.validateInstall(
            snapshot = snapshot,
            slot = WeaponAttachmentSlot.SCOPE,
            attachmentId = "tacz:scope_x4"
        )

        assertFalse(check.accepted)
        assertEquals("SLOT_OCCUPIED", check.reasonCode)
        assertEquals(WeaponAttachmentSlot.SCOPE, check.conflictSlot)
    }

    @Test
    public fun `validateInstall should reject power-device conflict across slots`() {
        val snapshot = WeaponAttachmentSnapshot(scopeId = "tacz:scope_thermal")

        val check = WeaponAttachmentConflictRules.validateInstall(
            snapshot = snapshot,
            slot = WeaponAttachmentSlot.LASER,
            attachmentId = "tacz:laser_pointer"
        )

        assertFalse(check.accepted)
        assertEquals("TAG_CONFLICT_POWER_DEVICE", check.reasonCode)
        assertEquals(WeaponAttachmentSlot.SCOPE, check.conflictSlot)
    }

    @Test
    public fun `validateInstall should accept compatible installation`() {
        val snapshot = WeaponAttachmentSnapshot(
            scopeId = "tacz:scope_red_dot",
            muzzleId = "tacz:muzzle_brake"
        )

        val check = WeaponAttachmentConflictRules.validateInstall(
            snapshot = snapshot,
            slot = WeaponAttachmentSlot.GRIP,
            attachmentId = "tacz:grip_vertical"
        )

        assertTrue(check.accepted)
    }

    @Test
    public fun `inferTags should expose expected semantic tags`() {
        val thermalScopeTags = WeaponAttachmentConflictRules.inferTags("tacz:scope_thermal_x4")
        val laserTags = WeaponAttachmentConflictRules.inferTags("tacz:laser_pointer")

        assertTrue(thermalScopeTags.contains("power_device"))
        assertTrue(thermalScopeTags.contains("optic_high_zoom"))
        assertTrue(laserTags.contains("power_device"))
        assertTrue(laserTags.contains("laser_pointer"))
    }
}

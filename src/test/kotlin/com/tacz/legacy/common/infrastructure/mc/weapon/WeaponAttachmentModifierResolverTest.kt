package com.tacz.legacy.common.infrastructure.mc.weapon

import org.junit.Assert.assertEquals
import org.junit.Test

public class WeaponAttachmentModifierResolverTest {

    @Test
    public fun `resolver should accumulate known attachment modifiers`() {
        val snapshot = WeaponAttachmentSnapshot(
            scopeId = "tacz:scope_x4",
            muzzleId = "tacz:muzzle_brake",
            gripId = "tacz:vertical_grip"
        )

        val modifier = WeaponAttachmentModifierResolver.resolve(snapshot)

        assertEquals(0f, modifier.damageAdd, 0.0001f)
        assertEquals(0f, modifier.armorIgnoreAdd, 0.0001f)
        assertEquals(0.05f, modifier.headShotMultiplierAdd, 0.0001f)
        assertEquals(-0.08f, modifier.standInaccuracyAdd, 0.0001f)
        assertEquals(-0.20f, modifier.moveInaccuracyAdd, 0.0001f)
        assertEquals(-0.08f, modifier.sneakInaccuracyAdd, 0.0001f)
        assertEquals(0f, modifier.lieInaccuracyAdd, 0.0001f)
        assertEquals(-0.18f, modifier.aimInaccuracyAdd, 0.0001f)
        assertEquals(0.05f, modifier.knockbackAdd, 0.0001f)
        assertEquals(0, modifier.bonusMagazineSize)
    }
}

package com.tacz.legacy.common.infrastructure.mc.registry

import org.junit.Assert.assertEquals
import org.junit.Test

public class LegacyGunCategoryResolverTest {

    @Test
    public fun `resolveFromRawType should support TACZ enum tokens`() {
        assertEquals(LegacyGunTabType.RIFLE, LegacyGunCategoryResolver.resolveFromRawType("rifle"))
        assertEquals(LegacyGunTabType.SMG, LegacyGunCategoryResolver.resolveFromRawType("smg"))
        assertEquals(LegacyGunTabType.RPG, LegacyGunCategoryResolver.resolveFromRawType("tacz:rpg"))
    }

    @Test
    public fun `resolve should prefer TACZ-like category folder in source path`() {
        val resolved = LegacyGunCategoryResolver.resolve(
            gunId = "ak47",
            sourceId = "sample_pack/data/tacz/data/guns/rpg/rpg7.json"
        )

        assertEquals(LegacyGunTabType.RPG, resolved)
    }

    @Test
    public fun `resolve should fallback to gun id keyword when source path has no category folder`() {
        val resolved = LegacyGunCategoryResolver.resolve(
            gunId = "hk_mp5a5",
            sourceId = "sample_pack/data/tacz/data/guns/hk_mp5a5.json"
        )

        assertEquals(LegacyGunTabType.SMG, resolved)
    }

    @Test
    public fun `resolve should return unknown when category cannot be inferred`() {
        val resolved = LegacyGunCategoryResolver.resolve(
            gunId = "mystery_weapon",
            sourceId = "sample_pack/data/tacz/data/guns/mystery_weapon.json"
        )

        assertEquals(LegacyGunTabType.UNKNOWN, resolved)
    }
}

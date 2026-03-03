package com.tacz.legacy.common.infrastructure.mc.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class LegacyContentIdsTest {

    @Test
    public fun `item ids should be lowercase and unique`() {
        val ids = LegacyContentIds.itemIds()

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it == it.lowercase() })
        assertTrue(ids.contains(LegacyContentIds.AK47))
    }

    @Test
    public fun `block ids should be lowercase and unique`() {
        val ids = LegacyContentIds.blockIds()

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it == it.lowercase() })
        assertTrue(ids.contains(LegacyContentIds.WEAPON_WORKBENCH))
    }

}

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
        assertTrue(ids.contains(LegacyContentIds.GUN_SMITH_TABLE))
        assertTrue(ids.contains(LegacyContentIds.WORKBENCH_A))
        assertTrue(ids.contains(LegacyContentIds.WORKBENCH_B))
        assertTrue(ids.contains(LegacyContentIds.WORKBENCH_C))
        assertTrue(ids.contains(LegacyContentIds.STEEL_TARGET))
        assertTrue(ids.contains(LegacyContentIds.TARGET))
        assertTrue(ids.contains(LegacyContentIds.STATUE))
    }

    @Test
    public fun `workbench block ids should include all static workbench variants`() {
        val ids = LegacyContentIds.workbenchBlockIds()

        assertEquals(5, ids.size)
        assertTrue(ids.contains(LegacyContentIds.WEAPON_WORKBENCH))
        assertTrue(ids.contains(LegacyContentIds.GUN_SMITH_TABLE))
        assertTrue(ids.contains(LegacyContentIds.WORKBENCH_A))
        assertTrue(ids.contains(LegacyContentIds.WORKBENCH_B))
        assertTrue(ids.contains(LegacyContentIds.WORKBENCH_C))
    }

}

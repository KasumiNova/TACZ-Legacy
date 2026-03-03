package com.tacz.legacy.client.render.block

import com.tacz.legacy.common.infrastructure.mc.registry.LegacyContentIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class LegacySpecialBlockModelRegistryTest {

    @Test
    public fun `find should return builtin descriptor for registered block ids`() {
        val workbench = LegacySpecialBlockModelRegistry.find(LegacyContentIds.WEAPON_WORKBENCH)
        val target = LegacySpecialBlockModelRegistry.find(LegacyContentIds.STEEL_TARGET)

        assertNotNull(workbench)
        assertNotNull(target)
        assertEquals("tacz:block/gun_smith_table", workbench?.modelResourcePath)
        assertEquals("tacz:block/target", target?.modelResourcePath)
    }

    @Test
    public fun `find should normalize namespace and casing`() {
        val descriptor = LegacySpecialBlockModelRegistry.find("TaCZ:Weapon_Workbench")

        assertNotNull(descriptor)
        assertEquals(LegacyContentIds.WEAPON_WORKBENCH, descriptor?.blockRegistryPath)
    }

    @Test
    public fun `allBuiltin should be stable and sorted by block id`() {
        val all = LegacySpecialBlockModelRegistry.allBuiltin()
        val stats = LegacySpecialBlockModelRegistry.adaptationStats()

        assertEquals(2, all.size)
        assertEquals(LegacyContentIds.STEEL_TARGET, all[0].blockRegistryPath)
        assertEquals(LegacyContentIds.WEAPON_WORKBENCH, all[1].blockRegistryPath)
        assertTrue(all.all { it.modelResourcePath.startsWith("tacz:block/") })
        assertEquals(2, stats.adapterCount)
        assertEquals(0, stats.translucentAdapterCount)
        assertEquals(listOf(LegacyContentIds.STEEL_TARGET, LegacyContentIds.WEAPON_WORKBENCH), stats.blockRegistryPaths)
        assertNull(LegacySpecialBlockModelRegistry.find("minecraft:stone"))
    }

    @Test
    public fun `validateModelResources should report missing and valid classpath models`() {
        val report = LegacySpecialBlockModelRegistry.validateModelResources { classpathPath ->
            classpathPath.contains("gun_smith_table")
        }

        assertEquals(2, report.total)
        assertEquals(1, report.valid)
        assertEquals(1, report.missing)
        assertEquals(listOf("assets/tacz/models/block/target.json"), report.missingModelJsonClasspathPaths)
        assertEquals(LegacyContentIds.STEEL_TARGET, report.entries[0].blockRegistryPath)
        assertEquals("assets/tacz/models/block/target.json", report.entries[0].modelJsonClasspathPath)
        assertEquals(false, report.entries[0].exists)
        assertEquals(LegacyContentIds.WEAPON_WORKBENCH, report.entries[1].blockRegistryPath)
        assertEquals("assets/tacz/models/block/gun_smith_table.json", report.entries[1].modelJsonClasspathPath)
        assertEquals(true, report.entries[1].exists)
    }
}

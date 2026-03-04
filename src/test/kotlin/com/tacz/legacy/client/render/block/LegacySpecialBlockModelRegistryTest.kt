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
        val statue = LegacySpecialBlockModelRegistry.find(LegacyContentIds.STATUE)

        assertNotNull(workbench)
        assertNotNull(target)
        assertNotNull(statue)
        assertEquals("tacz:block/gun_smith_table", workbench?.modelResourcePath)
        assertEquals("tacz:block/target", target?.modelResourcePath)
        assertEquals("tacz:block/statue", statue?.modelResourcePath)
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

        assertEquals(8, all.size)
        assertEquals(all.map { it.blockRegistryPath }.sorted(), all.map { it.blockRegistryPath })
        assertTrue(all.any { it.blockRegistryPath == LegacyContentIds.WORKBENCH_A })
        assertTrue(all.any { it.blockRegistryPath == LegacyContentIds.WORKBENCH_B })
        assertTrue(all.any { it.blockRegistryPath == LegacyContentIds.WORKBENCH_C })
        assertTrue(all.any { it.blockRegistryPath == LegacyContentIds.TARGET })
        assertTrue(all.any { it.blockRegistryPath == LegacyContentIds.STATUE })
        assertTrue(all.all { it.modelResourcePath.startsWith("tacz:block/") })
        assertEquals(8, stats.adapterCount)
        assertEquals(0, stats.translucentAdapterCount)
        assertEquals(all.map { it.blockRegistryPath }, stats.blockRegistryPaths)
        assertNull(LegacySpecialBlockModelRegistry.find("minecraft:stone"))
    }

    @Test
    public fun `validateModelResources should report missing and valid classpath models`() {
        val report = LegacySpecialBlockModelRegistry.validateModelResources { classpathPath ->
            classpathPath.contains("gun_smith_table") || classpathPath.contains("target")
        }

        assertEquals(8, report.total)
        assertEquals(7, report.valid)
        assertEquals(1, report.missing)
        assertEquals(listOf("assets/tacz/models/block/statue.json"), report.missingModelJsonClasspathPaths)
        assertTrue(report.entries.any {
            it.blockRegistryPath == LegacyContentIds.STATUE &&
                it.modelJsonClasspathPath == "assets/tacz/models/block/statue.json" &&
                !it.exists
        })
        assertTrue(report.entries.any {
            it.blockRegistryPath == LegacyContentIds.WORKBENCH_A &&
                it.modelJsonClasspathPath == "assets/tacz/models/block/gun_smith_table.json" &&
                it.exists
        })
    }
}

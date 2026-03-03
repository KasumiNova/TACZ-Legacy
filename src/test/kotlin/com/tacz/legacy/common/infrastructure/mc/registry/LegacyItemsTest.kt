package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class LegacyItemsTest {

    @After
    public fun cleanupRegisteredCache() {
        LegacyItems.clearRegisteredStandaloneForTests()
    }

    @Test
    public fun `dynamic gun registry paths should normalize and deduplicate`() {
        val snapshot = snapshotWithGunIds(
            "M4A1",
            "m4a1",
            "vector 45",
            "hk416d",
            "weapon_debug_core"
        )

        val paths = LegacyItems.dynamicGunRegistryPaths(snapshot)

        assertEquals(listOf("hk416d", "m4a1", "vector_45"), paths)
    }

    @Test
    public fun `standalone should fallback to builtin gun when runtime snapshot empty`() {
        val items = LegacyItems.standalone(GunPackRuntimeSnapshot.empty())
            .mapNotNull { it.registryName?.path }

        assertEquals(listOf(LegacyContentIds.AK47, LegacyContentIds.WEAPON_DEBUG_CORE), items)
    }

    @Test
    public fun `standalone should prefer dynamic guns when runtime snapshot available`() {
        val items = LegacyItems.standalone(
            snapshotWithGunIds("m4a1", "ak12")
        ).mapNotNull { it.registryName?.path }

        assertEquals(listOf("ak12", "m4a1", LegacyContentIds.WEAPON_DEBUG_CORE), items)
        assertTrue(items.none { it == LegacyContentIds.AK47 })
    }

    @Test
    public fun `standalone dynamic guns should appear in both category tab and all-guns tab`() {
        val snapshot = snapshotWithSourceMap(
            linkedMapOf(
                "glock_17" to "sample_pack/data/tacz/data/guns/pistol/glock_17.json"
            )
        )

        val gun = LegacyItems.standalone(snapshot)
            .first { it.registryName?.path == "glock_17" }
            as LegacyGunItem

        assertTrue(gun.isVisibleInCreativeTab(LegacyCreativeTabs.GUN_PISTOL))
        assertTrue(gun.isVisibleInCreativeTab(LegacyCreativeTabs.GUN_ALL))
    }

    @Test
    public fun `dynamic descriptor should prefer runtime type metadata over path heuristic`() {
        val snapshot = snapshotWithSourceMap(
            sourceByGunId = linkedMapOf(
                "ak47" to "sample_pack/data/tacz/data/guns/rpg/ak47.json"
            ),
            gunTypeByGunId = mapOf("ak47" to "rifle")
        )

        val descriptor = LegacyItems.dynamicGunDescriptors(snapshot).single()

        assertEquals(LegacyGunTabType.RIFLE, descriptor.tabType)
        assertEquals(LegacyItems.GunCategoryResolutionSource.METADATA, descriptor.resolutionSource)
    }

    @Test
    public fun `classification stats should split metadata heuristic and unknown`() {
        val snapshot = snapshotWithSourceMap(
            sourceByGunId = linkedMapOf(
                "ak47" to "sample_pack/data/tacz/data/guns/rpg/ak47.json",
                "glock_17" to "sample_pack/data/tacz/data/guns/pistol/glock_17.json",
                "mystery_blaster" to "sample_pack/data/tacz/data/guns/misc/mystery_blaster.json"
            ),
            gunTypeByGunId = mapOf(
                "ak47" to "rifle"
            )
        )

        val stats = LegacyItems.classificationStats(snapshot)

        assertEquals(3, stats.total)
        assertEquals(1, stats.metadataMatched)
        assertEquals(1, stats.heuristicMatched)
        assertEquals(1, stats.unknownMatched)
        assertEquals(1, stats.metadataHintsAvailable)
        assertEquals(1, stats.metadataHintsUsed)
    }

    @Test
    public fun `prepareRegisteredStandalone should freeze item instances after first registration snapshot`() {
        val firstSnapshot = snapshotWithGunIds("ak47", "m4a1")
        val secondSnapshot = snapshotWithGunIds("ak47", "m4a1", "glock_17")

        val first = LegacyItems.prepareRegisteredStandalone(firstSnapshot)
        val second = LegacyItems.prepareRegisteredStandalone(secondSnapshot)

        assertSame(first, second)
        assertEquals(
            listOf("ak47", "m4a1", LegacyContentIds.WEAPON_DEBUG_CORE),
            first.mapNotNull { it.registryName?.path }
        )
    }

    private fun snapshotWithGunIds(vararg gunIds: String): GunPackRuntimeSnapshot {
        val sourceByGunId = linkedMapOf<String, String>()
        gunIds.forEachIndexed { index, gunId ->
            sourceByGunId[gunId] = "sample_${index}.json"
        }
        return snapshotWithSourceMap(sourceByGunId)
    }

    private fun snapshotWithSourceMap(
        sourceByGunId: LinkedHashMap<String, String>,
        gunTypeByGunId: Map<String, String> = emptyMap()
    ): GunPackRuntimeSnapshot {
        return GunPackRuntimeSnapshot(
            loadedAtEpochMillis = 0L,
            totalSources = sourceByGunId.size,
            loadedGunsBySourceId = emptyMap(),
            loadedGunsByAmmoId = emptyMap(),
            sourceIdByGunId = sourceByGunId,
            gunTypeByGunId = gunTypeByGunId,
            duplicateGunIdSources = emptyMap(),
            failedSources = emptySet(),
            warningSources = emptySet(),
            issueHistogram = emptyMap()
        )
    }
}

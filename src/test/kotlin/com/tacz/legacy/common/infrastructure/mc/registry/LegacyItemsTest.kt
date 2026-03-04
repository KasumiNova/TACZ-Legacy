package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilityRuntime
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilitySnapshot
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentDefinition
import com.tacz.legacy.common.domain.gunpack.GunBoltType
import com.tacz.legacy.common.domain.gunpack.GunBulletData
import com.tacz.legacy.common.domain.gunpack.GunData
import com.tacz.legacy.common.domain.gunpack.GunFeedType
import com.tacz.legacy.common.domain.gunpack.GunFireMode
import com.tacz.legacy.common.domain.gunpack.GunReloadData
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
        WeaponAttachmentCompatibilityRuntime.registry().clear()
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

        assertTrue(items.contains(LegacyContentIds.AK47))
        assertTrue(items.contains(LegacyContentIds.WEAPON_DEBUG_CORE))
    }

    @Test
    public fun `standalone should prefer dynamic guns when runtime snapshot available`() {
        val items = LegacyItems.standalone(
            snapshotWithGunIds("m4a1", "ak12")
        ).mapNotNull { it.registryName?.path }

        assertTrue(items.containsAll(listOf("ak12", "m4a1", LegacyContentIds.WEAPON_DEBUG_CORE)))
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
        val firstPaths = first.mapNotNull { it.registryName?.path }
        assertTrue(firstPaths.containsAll(listOf("ak47", "m4a1", LegacyContentIds.WEAPON_DEBUG_CORE)))
    }

    @Test
    public fun `dynamic attachment descriptors should be generated from runtime attachment catalog`() {
        WeaponAttachmentCompatibilityRuntime.registry().replace(
            WeaponAttachmentCompatibilitySnapshot(
                loadedAtEpochMillis = 0L,
                attachmentsById = mapOf(
                    "tacz:sight_rmr_dot" to WeaponAttachmentDefinition(
                        attachmentId = "tacz:sight_rmr_dot",
                        attachmentType = "SCOPE",
                        sourceId = "sample_pack/data/tacz/index/attachments/sight_rmr_dot.json",
                        displayId = "tacz:sight_rmr_dot_display",
                        iconTextureAssetPath = "assets/tacz/textures/attachment/slot/sight_rmr_dot.png"
                    )
                ),
                allowEntriesByGunId = emptyMap(),
                tagsByTagId = emptyMap(),
                ammoIconTextureByAmmoId = emptyMap(),
                failedSources = emptySet()
            )
        )

        val descriptors = LegacyItems.dynamicAttachmentDescriptors()

        assertEquals(1, descriptors.size)
        assertEquals("attachment_tacz_sight_rmr_dot", descriptors.first().registryPath)
        assertEquals("tacz:sight_rmr_dot", descriptors.first().attachmentId)
        assertEquals("assets/tacz/textures/attachment/slot/sight_rmr_dot.png", descriptors.first().iconTextureAssetPath)
    }

    @Test
    public fun `dynamic ammo descriptors should aggregate ammo ids and infer registry paths`() {
        val ammo556 = sampleGunData(gunId = "m4a1", ammoId = "5.56_nato", ammoAmount = 30)
        val ammo9mm = sampleGunData(gunId = "glock_17", ammoId = "9mm", ammoAmount = 17)

        val snapshot = GunPackRuntimeSnapshot(
            loadedAtEpochMillis = 0L,
            totalSources = 2,
            loadedGunsBySourceId = mapOf(
                ammo556.sourceId to ammo556,
                ammo9mm.sourceId to ammo9mm
            ),
            loadedGunsByAmmoId = mapOf(
                "5.56_nato" to listOf(ammo556),
                "9mm" to listOf(ammo9mm)
            ),
            sourceIdByGunId = linkedMapOf(
                ammo556.gunId to ammo556.sourceId,
                ammo9mm.gunId to ammo9mm.sourceId
            ),
            gunTypeByGunId = emptyMap(),
            duplicateGunIdSources = emptyMap(),
            failedSources = emptySet(),
            warningSources = emptySet(),
            issueHistogram = emptyMap()
        )

        val descriptors = LegacyItems.dynamicAmmoDescriptors(snapshot)

        assertEquals(listOf("ammo_5_56_nato", "ammo_9mm"), descriptors.map { it.registryPath })
        assertEquals(listOf("ammo_box_5_56_nato", "ammo_box_9mm"), descriptors.map { it.boxRegistryPath })
        assertEquals(listOf(30, 17), descriptors.map { it.roundsPerItem })
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

    private fun sampleGunData(gunId: String, ammoId: String, ammoAmount: Int): GunData {
        return GunData(
            sourceId = "sample/$gunId.json",
            gunId = gunId,
            ammoId = ammoId,
            ammoAmount = ammoAmount,
            extendedMagAmmoAmount = null,
            canCrawl = false,
            canSlide = false,
            boltType = GunBoltType.CLOSED_BOLT,
            roundsPerMinute = 600,
            fireModes = setOf(GunFireMode.AUTO),
            bullet = GunBulletData(
                lifeSeconds = 3f,
                bulletAmount = 1,
                damage = 5f,
                speed = 20f,
                gravity = 0f,
                pierce = 1
            ),
            reload = GunReloadData(
                type = GunFeedType.MAGAZINE,
                infinite = false,
                emptyTimeSeconds = 2f,
                tacticalTimeSeconds = 1.5f
            )
        )
    }
}

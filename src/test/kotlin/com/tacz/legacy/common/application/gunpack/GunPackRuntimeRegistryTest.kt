package com.tacz.legacy.common.application.gunpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class GunPackRuntimeRegistryTest {

    @Test
    public fun `replace should build snapshot from batch report`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val registry = GunPackRuntimeRegistry()

        val report = analyzer.analyze(
            listOf(
                GunPackCompatibilitySource("valid.json", validGunJson()),
                GunPackCompatibilitySource("invalid.json", "{ bad-json }")
            )
        )

        val snapshot = registry.replace(report)

        assertEquals(2, snapshot.totalSources)
        assertEquals(1, snapshot.loadedCount)
        assertTrue(snapshot.failedSources.contains("invalid.json"))
        assertEquals(1, snapshot.issueHistogram["MALFORMED_JSON"])
        assertNotNull(snapshot.find("valid.json"))
        assertEquals(1, snapshot.findByAmmoId("tacz:556").size)
        assertNotNull(snapshot.findByGunId("valid_gun"))
        assertTrue(snapshot.conflictEntries().isEmpty())
        assertNull(snapshot.findConflict("valid_gun"))
    }

    @Test
    public fun `replace should keep deterministic winner on duplicate gunId conflicts`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val registry = GunPackRuntimeRegistry()

        val report = analyzer.analyze(
            listOf(
                GunPackCompatibilitySource("b.json", validGunJson(gunId = "same_gun", ammo = "tacz:556")),
                GunPackCompatibilitySource("a.json", validGunJson(gunId = "same_gun", ammo = "tacz:762"))
            )
        )

        val snapshot = registry.replace(report)

        assertEquals(1, snapshot.loadedCount)
        assertEquals("a.json", snapshot.sourceIdByGunId["same_gun"])
        assertEquals(listOf("a.json", "b.json"), snapshot.duplicateGunIdSources["same_gun"])
        assertEquals(1, snapshot.issueHistogram["DUPLICATE_GUN_ID_CONFLICT"])
        assertEquals(1, snapshot.findByAmmoId("tacz:762").size)
        assertEquals(0, snapshot.findByAmmoId("tacz:556").size)

        val conflict = snapshot.findConflict("same_gun")
        assertNotNull(conflict)
        assertEquals("same_gun", conflict?.gunId)
        assertEquals("a.json", conflict?.winnerSourceId)
        assertEquals(listOf("b.json"), conflict?.loserSourceIds)
    }

    @Test
    public fun `replace null and clear should reset snapshot`() {
        val registry = GunPackRuntimeRegistry()

        val emptyFromNull = registry.replace(null)
        assertEquals(0, emptyFromNull.totalSources)
        assertEquals(0, emptyFromNull.loadedCount)

        val cleared = registry.clear()
        assertEquals(0, cleared.totalSources)
        assertEquals(0, cleared.loadedCount)
    }

    private fun validGunJson(gunId: String = "valid_gun", ammo: String = "tacz:556"): String =
        """
        {
          "id": "$gunId",
          "ammo": "$ammo",
          "ammo_amount": 30,
          "bolt": "closed_bolt",
          "rpm": 700,
          "fire_mode": ["auto", "semi"],
          "reload": {
            "type": "magazine",
            "infinite": false,
            "feed": {
              "empty": 2.3,
              "tactical": 2.0
            }
          },
          "bullet": {
            "life": 9.0,
            "bullet_amount": 1,
            "damage": 5.8,
            "speed": 5.4,
            "gravity": 0.02,
            "pierce": 2
          }
        }
        """.trimIndent()

}
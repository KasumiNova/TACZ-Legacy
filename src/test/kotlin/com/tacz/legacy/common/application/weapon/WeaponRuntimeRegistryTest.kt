package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunPackCompatibilityBatchAnalyzer
import com.tacz.legacy.common.application.gunpack.GunPackCompatibilitySource
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeRegistry
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponRuntimeRegistryTest {

    @Test
    public fun `replaceFromGunPack should build definitions and support session creation`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(
            analyzer.analyze(
                listOf(
                    GunPackCompatibilitySource("ak47_data.json", validGunJson(gunId = "ak47", ammoAmount = 30)),
                    GunPackCompatibilitySource("bad.json", "{ bad-json }")
                )
            )
        )

        val registry = WeaponRuntimeRegistry()
        val snapshot = registry.replaceFromGunPack(gunPackSnapshot)

        assertEquals(1, snapshot.totalDefinitions)
        assertTrue(snapshot.failedGunIds.isEmpty())
        assertNotNull(snapshot.findDefinition("ak47"))

        val session = registry.createSession(gunId = "ak47", ammoReserve = 90)
        assertNotNull(session)
        assertEquals("ak47", session?.gunId)
        assertEquals(30, session?.machine?.snapshot()?.ammoInMagazine)
        assertEquals(90, session?.machine?.snapshot()?.ammoReserve)
        assertEquals(0.27f, session?.defaultBehaviorConfig?.bulletSpeed)
        assertEquals(0.02f, session?.defaultBehaviorConfig?.bulletGravity)
        assertEquals(0.01f, session?.defaultBehaviorConfig?.bulletFriction)
        assertEquals(5.8f, session?.defaultBehaviorConfig?.bulletDamage)
        assertEquals(180, session?.defaultBehaviorConfig?.bulletLifeTicks)
        assertEquals(2, session?.defaultBehaviorConfig?.bulletPierce)
        assertEquals(1, session?.defaultBehaviorConfig?.bulletPelletCount)

        val step = session?.machine?.dispatch(WeaponInput.TriggerPressed)
        assertTrue(step?.shotFired == true)
        assertEquals(29, step?.snapshot?.ammoInMagazine)
    }

    @Test
    public fun `createSession should clamp invalid initial ammo and return null for unknown gun`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(
            analyzer.analyze(
                listOf(
                    GunPackCompatibilitySource("smg_data.json", validGunJson(gunId = "smg", ammoAmount = 25))
                )
            )
        )

        val registry = WeaponRuntimeRegistry()
        registry.replaceFromGunPack(gunPackSnapshot)

        val clampedSession = registry.createSession(
            gunId = "smg",
            ammoReserve = -100,
            ammoInMagazine = 999
        )
        assertNotNull(clampedSession)
        assertEquals(25, clampedSession?.machine?.snapshot()?.ammoInMagazine)
        assertEquals(0, clampedSession?.machine?.snapshot()?.ammoReserve)

        val unknown = registry.createSession("unknown_gun")
        assertNull(unknown)
    }

    @Test
    public fun `createSession should build fallback definition when explicitly allowed`() {
        val registry = WeaponRuntimeRegistry()
        registry.clear()

        val session = registry.createSession(
            gunId = "unknown_gun",
            allowFallbackDefinition = true
        )

        assertNotNull(session)
        assertEquals("unknown_gun", session?.gunId)
        assertEquals(30, session?.machine?.snapshot()?.ammoInMagazine)
        assertEquals(5.0f, session?.defaultBehaviorConfig?.bulletSpeed)
        assertEquals(5.0f, session?.defaultBehaviorConfig?.bulletDamage)
    }

    @Test
    public fun `replaceFromGunPack should reset to empty for empty runtime snapshot`() {
        val registry = WeaponRuntimeRegistry()
        val emptyGunPackSnapshot = GunPackRuntimeRegistry().replace(null)

        val snapshot = registry.replaceFromGunPack(emptyGunPackSnapshot)
        assertEquals(0, snapshot.totalDefinitions)
        assertTrue(snapshot.definitionsByGunId.isEmpty())
    }

    @Test
    public fun `createSessionFromSnapshot should preserve authoritative state fields`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(
            analyzer.analyze(
                listOf(
                    GunPackCompatibilitySource("ak47_data.json", validGunJson(gunId = "ak47", ammoAmount = 30))
                )
            )
        )

        val registry = WeaponRuntimeRegistry()
        registry.replaceFromGunPack(gunPackSnapshot)

        val session = registry.createSessionFromSnapshot(
            gunId = "ak47",
            authoritativeSnapshot = WeaponSnapshot(
                state = WeaponState.RELOADING,
                ammoInMagazine = 12,
                ammoReserve = 77,
                isTriggerHeld = false,
                reloadTicksRemaining = 10,
                cooldownTicksRemaining = 3,
                semiLocked = true,
                burstShotsRemaining = 0,
                totalShotsFired = 42
            )
        )

        assertNotNull(session)
        val snapshot = session?.machine?.snapshot()
        assertEquals(WeaponState.RELOADING, snapshot?.state)
        assertEquals(12, snapshot?.ammoInMagazine)
        assertEquals(77, snapshot?.ammoReserve)
        assertEquals(10, snapshot?.reloadTicksRemaining)
        assertEquals(3, snapshot?.cooldownTicksRemaining)
        assertEquals(true, snapshot?.semiLocked)
        assertEquals(0, snapshot?.burstShotsRemaining)
        assertEquals(42, snapshot?.totalShotsFired)
    }

    @Test
    public fun `createSessionFromSnapshot should normalize invalid ammo and reloading ticks`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(
            analyzer.analyze(
                listOf(
                    GunPackCompatibilitySource("smg_data.json", validGunJson(gunId = "smg", ammoAmount = 25))
                )
            )
        )

        val registry = WeaponRuntimeRegistry()
        registry.replaceFromGunPack(gunPackSnapshot)

        val session = registry.createSessionFromSnapshot(
            gunId = "smg",
            authoritativeSnapshot = WeaponSnapshot(
                state = WeaponState.RELOADING,
                ammoInMagazine = 999,
                ammoReserve = 20,
                reloadTicksRemaining = 0
            )
        )

        assertNotNull(session)
        val snapshot = session?.machine?.snapshot()
        assertEquals(25, snapshot?.ammoInMagazine)
        assertEquals(20, snapshot?.ammoReserve)
        assertTrue((snapshot?.reloadTicksRemaining ?: 0) > 0)
    }

    @Test
    public fun `replaceFromGunPack should apply fire mode adjust and lua script params`() {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(
            analyzer.analyze(
                listOf(
                    GunPackCompatibilitySource(
                        "ak47_adjusted_data.json",
                        adjustedGunJson(gunId = "ak47")
                    )
                )
            )
        )

        val registry = WeaponRuntimeRegistry()
        val snapshot = registry.replaceFromGunPack(gunPackSnapshot)
        val definition = snapshot.findDefinition("ak47")

        assertNotNull(definition)
        assertEquals(360, definition?.spec?.roundsPerMinute)
        assertEquals(0.2f, definition?.ballistics?.speed ?: 0f, 0.0001f)
        assertEquals(13.75f, definition?.ballistics?.damage ?: 0f, 0.0001f)
        assertEquals(3.0f, definition?.ballistics?.knockback ?: 0f, 0.0001f)
        assertEquals(0.5f, definition?.ballistics?.inaccuracy?.stand ?: 0f, 0.0001f)
        assertEquals(2.6f, definition?.ballistics?.inaccuracy?.aim ?: 0f, 0.0001f)

        val session = registry.createSession("ak47")
        assertNotNull(session)
        assertEquals(0.2f, session?.defaultBehaviorConfig?.bulletSpeed ?: 0f, 0.0001f)
        assertEquals(13.75f, session?.defaultBehaviorConfig?.bulletDamage ?: 0f, 0.0001f)
        assertEquals(0.5f, session?.defaultBehaviorConfig?.bulletInaccuracyDegrees ?: 0f, 0.0001f)
    }

    private fun validGunJson(gunId: String, ammoAmount: Int): String =
        """
        {
          "id": "$gunId",
          "ammo": "tacz:556",
          "ammo_amount": $ammoAmount,
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

        private fun adjustedGunJson(gunId: String): String =
                """
                {
                    "id": "$gunId",
                    "ammo": "tacz:556",
                    "ammo_amount": 30,
                    "bolt": "closed_bolt",
                    "rpm": 600,
                    "fire_mode": ["auto"],
                    "reload": {
                        "type": "magazine",
                        "infinite": false,
                        "feed": {
                            "empty": 2.0,
                            "tactical": 1.8
                        }
                    },
                    "inaccuracy": {
                        "stand": 1.0,
                        "move": 2.0,
                        "sneak": 3.0,
                        "lie": 4.0,
                        "aim": 5.0
                    },
                    "bullet": {
                        "life": 10.0,
                        "bullet_amount": 1,
                        "damage": 10.0,
                        "speed": 6.0,
                        "gravity": 0.02,
                        "friction": 0.1,
                        "pierce": 2,
                        "knockback": 1.2
                    },
                    "fire_mode_adjust": {
                        "AUTO": {
                            "damage": 2.5,
                            "rpm": 120,
                            "speed": 2.0,
                            "knockback": 0.3,
                            "aim_inaccuracy": 1.5,
                            "other_inaccuracy": 0.25
                        }
                    },
                    "script_param": {
                        "lua_damage_scale": 1.1,
                        "lua_speed_scale": 0.5,
                        "lua_knockback_scale": 2.0,
                        "lua_inaccuracy_scale": 0.4,
                        "lua_rpm_scale": 0.5
                    }
                }
                """.trimIndent()

}
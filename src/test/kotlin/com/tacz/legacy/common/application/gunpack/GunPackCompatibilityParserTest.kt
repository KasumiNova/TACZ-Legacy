package com.tacz.legacy.common.application.gunpack

import com.tacz.legacy.common.domain.gunpack.GunBoltType
import com.tacz.legacy.common.domain.gunpack.GunDefaults
import com.tacz.legacy.common.domain.gunpack.GunFeedType
import com.tacz.legacy.common.domain.gunpack.GunFireMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class GunPackCompatibilityParserTest {

    private val parser: GunPackCompatibilityParser = GunPackCompatibilityParser()

    @Test
    public fun `parser should parse canonical TACZ gun data fields`() {
        val json =
            """
            {
              "id": "AK47",
              "ammo": "tacz:9mm",
              "ammo_amount": 30,
              "bolt": "closed_bolt",
              "rpm": 720,
              "aim_time": 0.35,
              "fire_mode": ["auto", "semi"],
              "reload": {
                "type": "magazine",
                "infinite": false,
                "feed": {
                  "empty": 2.6,
                  "tactical": 2.2
                }
              },
              "bullet": {
                "life": 9.5,
                "bullet_amount": 1,
                "damage": 6.0,
                "speed": 5.5,
                "gravity": 0.03,
                "pierce": 2
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:canonical")
        val gunData = result.gunData

        assertNotNull(gunData)
        assertFalse(result.report.hasErrors())
        assertEquals("test:canonical", gunData?.sourceId)
        assertEquals("ak47", gunData?.gunId)
        assertEquals("tacz:9mm", gunData?.ammoId)
        assertEquals(30, gunData?.ammoAmount)
        assertEquals(GunBoltType.CLOSED_BOLT, gunData?.boltType)
        assertEquals(720, gunData?.roundsPerMinute)
        assertEquals(0.35f, gunData?.aimTimeSeconds ?: 0f, 0.0001f)
        assertEquals(setOf(GunFireMode.AUTO, GunFireMode.SEMI), gunData?.fireModes)
        assertEquals(4.5f, gunData?.inaccuracy?.stand ?: 0f, 0.0001f)
        assertEquals(5.0f, gunData?.inaccuracy?.move ?: 0f, 0.0001f)
        assertEquals(GunFeedType.MAGAZINE, gunData?.reload?.type)
        assertEquals(2, gunData?.bullet?.pierce)
    }

    @Test
    public fun `parser should parse and normalize inaccuracy object`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "fire_mode": ["semi"],
              "inaccuracy": {
                "stand": 3.2,
                "move": -6.0,
                "sneak": 1.1,
                "lie": 0.8,
                "aim": 0.05
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:inaccuracy")

        assertNotNull(result.gunData)
        assertEquals(3.2f, result.gunData?.inaccuracy?.stand ?: 0f, 0.0001f)
        assertEquals(0.0f, result.gunData?.inaccuracy?.move ?: 0f, 0.0001f)
        assertEquals(1.1f, result.gunData?.inaccuracy?.sneak ?: 0f, 0.0001f)
        assertEquals(0.8f, result.gunData?.inaccuracy?.lie ?: 0f, 0.0001f)
        assertEquals(0.05f, result.gunData?.inaccuracy?.aim ?: 0f, 0.0001f)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_VALUE" && it.field == "inaccuracy.move" })
    }

    @Test
    public fun `parser should accept alias fields and emit diagnostics`() {
        val json =
            """
            {
              "ammo_id": "tacz:556",
              "magazine_size": 35,
              "rounds_per_minute": 800,
              "fire_modes": ["full_auto", "semi_auto"],
              "reload": {
                "feed_type": "manual"
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:alias")
        val gunData = result.gunData

        assertNotNull(gunData)
        assertFalse(result.report.hasErrors())
        assertTrue(result.report.allIssues().any { it.code == "FIELD_ALIAS_USED" })
        assertEquals("alias", gunData?.gunId)
        assertEquals("tacz:556", gunData?.ammoId)
        assertEquals(35, gunData?.ammoAmount)
        assertEquals(800, gunData?.roundsPerMinute)
        assertEquals(setOf(GunFireMode.AUTO, GunFireMode.SEMI), gunData?.fireModes)
        assertEquals(GunFeedType.MANUAL, gunData?.reload?.type)
    }

    @Test
    public fun `parser should normalize invalid numeric values with issues`() {
        val json =
            """
            {
              "ammo": "tacz:test",
              "ammo_amount": 0,
              "rpm": -100,
              "aim_time": -0.5,
              "fire_mode": ["nonsense_mode"],
              "bullet": {
                "bullet_amount": 0,
                "damage": -1.0
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:normalize")
        val gunData = result.gunData

        assertNotNull(gunData)
        assertTrue(result.report.hasErrors())
        assertTrue(result.report.hasWarnings())
        assertEquals(GunDefaults.AMMO_AMOUNT, gunData?.ammoAmount)
        assertEquals(GunDefaults.RPM, gunData?.roundsPerMinute)
        assertEquals(GunDefaults.AIM_TIME_SECONDS, gunData?.aimTimeSeconds ?: 0f, 0.0001f)
        assertEquals(GunDefaults.BULLET_AMOUNT, gunData?.bullet?.bulletAmount)
        assertEquals(GunDefaults.BULLET_DAMAGE, gunData?.bullet?.damage)
        assertEquals(setOf(GunFireMode.SEMI), gunData?.fireModes)
    }

    @Test
    public fun `parser should return null gun data for malformed json`() {
        val result = parser.parseGunDataJson("{ not-json }", "test:bad")

        assertNull(result.gunData)
        assertTrue(result.report.hasErrors())
        assertTrue(result.report.allIssues().any { it.code == "MALFORMED_JSON" })
    }

    @Test
    public fun `parser should accept json with line and block comments`() {
      val json =
        """
        {
          // inline comment
          "ammo": "tacz:9mm",
          "ammo_amount": 20,
          /* multi-line
           comment */
          "rpm": 500,
          "fire_mode": ["semi"]
        }
        """.trimIndent()

      val result = parser.parseGunDataJson(json, "commented:sample_data.json")

      assertNotNull(result.gunData)
      assertFalse(result.report.hasErrors())
      assertEquals("sample", result.gunData?.gunId)
    }

    @Test
    public fun `parser should return error when root is not json object`() {
        val result = parser.parseGunDataJson("[]", "test:root-array")

        assertNull(result.gunData)
        assertTrue(result.report.hasErrors())
        assertTrue(result.report.allIssues().any { it.code == "MALFORMED_JSON" })
    }

    @Test
    public fun `parser should fail when required ammo field has wrong type`() {
        val json =
            """
            {
              "id": "ak47",
              "ammo": 123,
              "fire_mode": ["semi"]
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:invalid-ammo")

        assertNull(result.gunData)
        assertTrue(result.report.hasErrors())
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "ammo" })
        assertTrue(result.report.allIssues().any { it.code == "MISSING_REQUIRED_FIELD" && it.field == "ammo" })
    }

    @Test
    public fun `parser should support single-string fire_mode tokens`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "fire_mode": "auto|semi"
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:fire-mode-string")

        assertNotNull(result.gunData)
        assertEquals(setOf(GunFireMode.AUTO, GunFireMode.SEMI), result.gunData?.fireModes)
    }

    @Test
    public fun `parser should fallback fire_mode when invalid field type encountered`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "fire_mode": 1
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:fire-mode-invalid-type")

        assertNotNull(result.gunData)
        assertEquals(setOf(GunFireMode.SEMI), result.gunData?.fireModes)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "fire_mode" })
    }

    @Test
    public fun `parser should use reload cooldown timings when feed is missing`() {
        val json =
            """
            {
              "ammo": "tacz:556",
              "reload": {
                "type": "magazine",
                "cooldown": {
                  "empty": 3.0,
                  "tactical": 2.5
                }
              },
              "fire_mode": ["semi"]
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:reload-cooldown-fallback")

        assertNotNull(result.gunData)
        assertEquals(3.0f, result.gunData?.reload?.emptyTimeSeconds ?: 0f, 0.0001f)
        assertEquals(2.5f, result.gunData?.reload?.tacticalTimeSeconds ?: 0f, 0.0001f)
        assertTrue(result.report.allIssues().any { it.code == "FIELD_ALIAS_USED" && it.field == "reload.feed" })
    }

    @Test
    public fun `parser should fallback reload and bullet defaults when field types are invalid`() {
        val json =
            """
            {
              "ammo": "tacz:308",
              "reload": "invalid",
              "bullet": 5,
              "fire_mode": ["semi"]
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:invalid-object-types")

        assertNotNull(result.gunData)
        assertEquals(GunDefaults.RELOAD_EMPTY_TIME, result.gunData?.reload?.emptyTimeSeconds ?: 0f, 0.0001f)
        assertEquals(GunDefaults.BULLET_DAMAGE, result.gunData?.bullet?.damage ?: 0f, 0.0001f)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "reload" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "bullet" })
    }

    @Test
    public fun `parser should accept extended magazine alias and report short list`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "extended_magazine_size": [40, 50],
              "fire_mode": ["semi"]
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:extended-mag-alias")

        assertNotNull(result.gunData)
        assertEquals(listOf(40, 50), result.gunData?.extendedMagAmmoAmount)
        assertTrue(result.report.allIssues().any { it.code == "FIELD_ALIAS_USED" && it.field == "extended_mag_ammo_amount" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_VALUE" && it.field == "extended_mag_ammo_amount" })
    }

    @Test
    public fun `parser should derive unknown gun id when source fallback is blank`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "fire_mode": ["semi"]
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, ":")

        assertNotNull(result.gunData)
        assertEquals("unknown_gun", result.gunData?.gunId)
        assertTrue(result.report.allIssues().any { it.code == "MISSING_OPTIONAL_FIELD" && it.field == "id" })
    }

      @Test
      public fun `parser should fallback primitive fields when numeric and boolean types are invalid`() {
        val json =
          """
          {
            "ammo": "tacz:9mm",
            "ammo_amount": "30",
            "rpm": "900",
            "aim_time": "quick",
            "can_crawl": "true",
            "extended_mag_ammo_amount": [40, "x", 50],
            "fire_mode": ["semi"]
          }
          """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:primitive-fallback")

        assertNotNull(result.gunData)
        assertEquals(GunDefaults.AMMO_AMOUNT, result.gunData?.ammoAmount)
        assertEquals(GunDefaults.RPM, result.gunData?.roundsPerMinute)
        assertEquals(GunDefaults.AIM_TIME_SECONDS, result.gunData?.aimTimeSeconds ?: 0f, 0.0001f)
        assertTrue(result.gunData?.canCrawl ?: false)
        assertEquals(listOf(40, 50), result.gunData?.extendedMagAmmoAmount)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "ammo_amount" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "rpm" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "aim_time" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "can_crawl" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "extended_mag_ammo_amount" })
      }

      @Test
      public fun `parser should ignore non-string fire mode entries but keep valid ones`() {
        val json =
          """
          {
            "ammo": "tacz:9mm",
            "fire_mode": [true, 1, "semi"]
          }
          """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:fire-mode-mixed-array")

        assertNotNull(result.gunData)
        assertEquals(setOf(GunFireMode.SEMI), result.gunData?.fireModes)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "fire_mode" })
      }

    @Test
    public fun `parser should preserve slash-like content inside json strings`() {
        val json =
            """
            {
              "id": "AK/*47//x",
              "ammo": "tacz://9mm",
              "fire_mode": ["semi"]
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:string-with-comment-markers")

        assertNotNull(result.gunData)
        assertEquals("ak_47_x", result.gunData?.gunId)
        assertEquals("tacz://9mm", result.gunData?.ammoId)
        assertFalse(result.report.hasErrors())
    }

    @Test
    public fun `parser should parse recoil keyframes and crawl recoil multiplier`() {
        val json =
            """
            {
              "ammo": "tacz:556",
              "fire_mode": ["semi"],
              "crawl_recoil_multiplier": 0.6,
              "recoil": {
                "pitch": [
                  {"time": 0.0, "value": [0.5, 0.7]},
                  {"time": 0.2, "value": [0.2, 0.2]}
                ],
                "yaw": [
                  {"time": 0.0, "value": [-0.3, 0.1]}
                ]
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:recoil")

        assertNotNull(result.gunData)
        assertEquals(0.6f, result.gunData?.crawlRecoilMultiplier ?: 0f, 0.0001f)
        assertEquals(2, result.gunData?.recoil?.pitch?.size)
        assertEquals(1, result.gunData?.recoil?.yaw?.size)
        assertEquals(0.0f, result.gunData?.recoil?.pitch?.get(0)?.timeSeconds ?: 0f, 0.0001f)
        assertEquals(0.5f, result.gunData?.recoil?.pitch?.get(0)?.valueMin ?: 0f, 0.0001f)
        assertEquals(0.7f, result.gunData?.recoil?.pitch?.get(0)?.valueMax ?: 0f, 0.0001f)
        assertFalse(result.report.hasErrors())
    }

    @Test
    public fun `parser should normalize invalid recoil fields`() {
        val json =
            """
            {
              "ammo": "tacz:556",
              "fire_mode": ["semi"],
              "crawl_recoil_multiplier": -1,
              "recoil": {
                "pitch": [
                  {"time": -0.1, "value": [0.8, 0.1]},
                  {"time": 0.3, "value": ["bad", 0.1]}
                ]
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:recoil-normalize")

        assertNotNull(result.gunData)
        assertEquals(GunDefaults.CRAWL_RECOIL_MULTIPLIER, result.gunData?.crawlRecoilMultiplier ?: 0f, 0.0001f)
        assertEquals(1, result.gunData?.recoil?.pitch?.size)
        assertEquals(0.0f, result.gunData?.recoil?.pitch?.firstOrNull()?.timeSeconds ?: -1f, 0.0001f)
        assertEquals(0.1f, result.gunData?.recoil?.pitch?.firstOrNull()?.valueMin ?: 0f, 0.0001f)
        assertEquals(0.8f, result.gunData?.recoil?.pitch?.firstOrNull()?.valueMax ?: 0f, 0.0001f)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_VALUE" && it.field == "crawl_recoil_multiplier" })
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_VALUE" && it.field == "recoil.pitch[0].time" })
    }

    @Test
    public fun `parser should parse script param numeric map`() {
        val json =
            """
            {
              "ammo": "tacz:556",
              "fire_mode": ["semi"],
              "script_param": {
                "bolt_feed_time": 0.12,
                "shoot_feed_time": 0.24,
                "BOLT_TIME": 0.33,
                "invalid": "oops"
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:script-param")

        assertNotNull(result.gunData)
        assertEquals(0.12f, result.gunData?.scriptParams?.get("bolt_feed_time") ?: 0f, 0.0001f)
        assertEquals(0.24f, result.gunData?.scriptParams?.get("shoot_feed_time") ?: 0f, 0.0001f)
        assertEquals(0.33f, result.gunData?.scriptParams?.get("bolt_time") ?: 0f, 0.0001f)
        assertTrue(result.report.allIssues().any { it.code == "INVALID_FIELD_TYPE" && it.field == "script_param.invalid" })
    }

    @Test
    public fun `parser should parse extra_damage with distance decay table`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "ammo_amount": 30,
              "bullet": {
                "damage": 10.0,
                "speed": 5.0,
                "extra_damage": {
                  "armor_ignore": 0.35,
                  "head_shot_multiplier": 2.5,
                  "damage_adjust": [
                    { "distance": 10, "damage": 10.0 },
                    { "distance": 50, "damage": 7.0 },
                    { "distance": "infinite", "damage": 3.0 }
                  ]
                }
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:extra-damage")
        val gunData = result.gunData

        assertNotNull(gunData)
        assertFalse(result.report.hasErrors())
        val extra = gunData!!.bullet.extraDamage
        assertEquals(0.35f, extra.armorIgnore, 0.0001f)
        assertEquals(2.5f, extra.headShotMultiplier, 0.0001f)
        assertEquals(3, extra.damageAdjust.size)
        assertEquals(10.0f, extra.damageAdjust[0].distance, 0.0001f)
        assertEquals(10.0f, extra.damageAdjust[0].damage, 0.0001f)
        assertEquals(50.0f, extra.damageAdjust[1].distance, 0.0001f)
        assertEquals(7.0f, extra.damageAdjust[1].damage, 0.0001f)
        assertEquals(Float.MAX_VALUE, extra.damageAdjust[2].distance, 0.0001f)
        assertEquals(3.0f, extra.damageAdjust[2].damage, 0.0001f)
    }

    @Test
    public fun `parser should default extra_damage when section is missing`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "ammo_amount": 30,
              "bullet": {
                "damage": 10.0,
                "speed": 5.0
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:no-extra")
        val gunData = result.gunData

        assertNotNull(gunData)
        val extra = gunData!!.bullet.extraDamage
        assertEquals(0f, extra.armorIgnore, 0.0001f)
        assertEquals(1f, extra.headShotMultiplier, 0.0001f)
        assertTrue(extra.damageAdjust.isEmpty())
    }

    @Test
    public fun `parser should clamp armor_ignore to 0-1 range`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "ammo_amount": 30,
              "bullet": {
                "damage": 10.0,
                "speed": 5.0,
                "extra_damage": {
                  "armor_ignore": 1.5,
                  "head_shot_multiplier": -1.0
                }
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:clamp")
        val extra = result.gunData!!.bullet.extraDamage
        assertEquals(1.0f, extra.armorIgnore, 0.0001f)
        assertEquals(0.0f, extra.headShotMultiplier, 0.0001f)
    }

    @Test
    public fun `parser should warn on invalid distance in damage_adjust`() {
        val json =
            """
            {
              "ammo": "tacz:9mm",
              "ammo_amount": 30,
              "bullet": {
                "damage": 10.0,
                "speed": 5.0,
                "extra_damage": {
                  "damage_adjust": [
                    { "distance": "bad", "damage": 5.0 },
                    { "distance": 20, "damage": 8.0 }
                  ]
                }
              }
            }
            """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:bad-distance")
        val extra = result.gunData!!.bullet.extraDamage
        assertEquals(1, extra.damageAdjust.size)
        assertEquals(20.0f, extra.damageAdjust[0].distance, 0.0001f)
        assertTrue(result.report.allIssues().any { it.field.contains("damage_adjust[0].distance") })
    }

    // ---- ignite / explosion / knockback / tracer ----

    @Test
    public fun `parser should parse bullet ignite as boolean`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": {
                "damage": 5.0,
                "speed": 3.0,
                "ignite": true,
                "ignite_entity_time": 5
              }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:ignite-bool")
        val b = result.gunData!!.bullet
        assertTrue(b.ignite.entity)
        assertTrue(b.ignite.block)
        assertEquals(5, b.igniteEntityTime)
    }

    @Test
    public fun `parser should parse bullet ignite as object`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": {
                "damage": 5.0,
                "speed": 3.0,
                "ignite": { "entity": true, "block": false }
              }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:ignite-obj")
        val b = result.gunData!!.bullet
        assertTrue(b.ignite.entity)
        assertFalse(b.ignite.block)
    }

    @Test
    public fun `parser should parse explosion data`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": {
                "damage": 5.0,
                "speed": 3.0,
                "explosion": {
                  "explode": true,
                  "radius": 3.5,
                  "damage": 10.0,
                  "knockback": true,
                  "destroy_block": true,
                  "delay": 1.5
                }
              }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:explosion")
        val exp = result.gunData!!.bullet.explosion
        assertNotNull(exp)
        assertTrue(exp!!.explode)
        assertEquals(3.5f, exp.radius, 0.001f)
        assertEquals(10.0f, exp.damage, 0.001f)
        assertTrue(exp.knockback)
        assertTrue(exp.destroyBlock)
        assertEquals(1.5f, exp.delaySeconds, 0.001f)
    }

    @Test
    public fun `parser should return null when explosion absent`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": { "damage": 5.0, "speed": 3.0 }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:no-explosion")
        assertNull(result.gunData!!.bullet.explosion)
    }

    @Test
    public fun `parser should parse knockback`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": { "damage": 5.0, "speed": 3.0, "knockback": 2.5 }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:knockback")
        assertEquals(2.5f, result.gunData!!.bullet.knockback, 0.001f)
    }

    @Test
    public fun `parser should parse tracer_count_interval`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": { "damage": 5.0, "speed": 3.0, "tracer_count_interval": 3 }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:tracer")
        assertEquals(3, result.gunData!!.bullet.tracerCountInterval)
    }

    // ---- burst / moveSpeed / melee / fireModeAdjust ----

    @Test
    public fun `parser should parse burst_data`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "burst_data": {
                "continuous_shoot": true,
                "count": 5,
                "bpm": 300,
                "min_interval": 0.5
              }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:burst")
        val burst = result.gunData!!.burstData
        assertTrue(burst.continuousShoot)
        assertEquals(5, burst.count)
        assertEquals(300, burst.bpm)
        assertEquals(0.5, burst.minInterval, 0.001)
    }

    @Test
    public fun `parser should parse movement_speed`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "movement_speed": { "base": 0.9, "aim": 0.7, "reload": 0.5 }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:moveSpeed")
        val ms = result.gunData!!.moveSpeed
        assertEquals(0.9f, ms.baseMultiplier, 0.001f)
        assertEquals(0.7f, ms.aimMultiplier, 0.001f)
        assertEquals(0.5f, ms.reloadMultiplier, 0.001f)
    }

    @Test
    public fun `parser should parse melee with default sub-block`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "melee": {
                "distance": 2.0,
                "cooldown": 0.8,
                "default": {
                  "animation_type": "melee_slash",
                  "distance": 1.5,
                  "range_angle": 45.0,
                  "cooldown": 0.5,
                  "damage": 8.0,
                  "knockback": 0.3,
                  "prep": 0.2
                }
              }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:melee")
        val melee = result.gunData!!.melee
        assertEquals(2.0f, melee.distance, 0.001f)
        assertEquals(0.8f, melee.cooldownSeconds, 0.001f)
        assertNotNull(melee.defaultMelee)
        val dm = melee.defaultMelee!!
        assertEquals("melee_slash", dm.animationType)
        assertEquals(1.5f, dm.distance, 0.001f)
        assertEquals(45.0f, dm.rangeAngle, 0.001f)
        assertEquals(8.0f, dm.damage, 0.001f)
        assertEquals(0.3f, dm.knockback, 0.001f)
        assertEquals(0.2f, dm.prepTimeSeconds, 0.001f)
    }

    @Test
    public fun `parser should parse fire_mode_adjust map`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "fire_mode": ["auto", "semi"],
              "fire_mode_adjust": {
                "semi": {
                  "damage": 2.0,
                  "rpm": 100,
                  "speed": 0.5,
                  "knockback": 0.1,
                  "armor_ignore": 0.05,
                  "head_shot_multiplier": 0.5,
                  "aim_inaccuracy": -0.3,
                  "other_inaccuracy": 0.2
                },
                "auto": {
                  "damage": -1.0
                }
              }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:fma")
        val fma = result.gunData!!.fireModeAdjust
        assertEquals(2, fma.size)
        val semi = fma["SEMI"]!!
        assertEquals(2.0f, semi.damageAmount, 0.001f)
        assertEquals(100, semi.roundsPerMinute)
        assertEquals(0.5f, semi.speed, 0.001f)
        assertEquals(0.1f, semi.knockback, 0.001f)
        assertEquals(0.05f, semi.armorIgnore, 0.001f)
        assertEquals(0.5f, semi.headShotMultiplier, 0.001f)
        assertEquals(-0.3f, semi.aimInaccuracy, 0.001f)
        assertEquals(0.2f, semi.otherInaccuracy, 0.001f)
        val auto = fma["AUTO"]!!
        assertEquals(-1.0f, auto.damageAmount, 0.001f)
        assertEquals(0, auto.roundsPerMinute)
    }

    @Test
    public fun `parser should produce defaults when optional blocks absent`() {
        val json = """
            {
              "ammo": "tacz:9mm",
              "bullet": { "damage": 5.0, "speed": 3.0 }
            }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:defaults")
        val g = result.gunData!!
        assertFalse(g.bullet.ignite.entity)
        assertFalse(g.bullet.ignite.block)
        assertEquals(0f, g.bullet.knockback, 0.001f)
        assertEquals(-1, g.bullet.tracerCountInterval)
        assertNull(g.bullet.explosion)
        assertFalse(g.burstData.continuousShoot)
        assertEquals(0f, g.moveSpeed.baseMultiplier, 0.001f)
        assertEquals(1f, g.melee.distance, 0.001f)
        assertNull(g.melee.defaultMelee)
        assertTrue(g.fireModeAdjust.isEmpty())
    }

    @Test
    public fun `parser should normalize allow attachment types and ignore unsupported values`() {
        val json = """
          {
            "ammo": "tacz:9mm",
            "fire_mode": ["semi"],
            "allow_attachment_types": [
              "scope",
              "extended_magazine",
              "tacz:laser",
              "under_barrel",
              true
            ]
          }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:allow-types")
        val gunData = result.gunData

        assertNotNull(gunData)
        assertEquals(setOf("SCOPE", "EXTENDED_MAG", "LASER"), gunData?.allowAttachmentTypes)
        assertTrue(result.report.allIssues().any {
            it.code == "UNSUPPORTED_ENUM_VALUE" && it.field == "allow_attachment_types[3]"
        })
        assertTrue(result.report.allIssues().any {
            it.code == "INVALID_FIELD_TYPE" && it.field == "allow_attachment_types[4]"
        })
    }

    @Test
    public fun `parser should normalize allow attachments and ignore malformed entries`() {
        val json = """
          {
            "ammo": "tacz:9mm",
            "fire_mode": ["semi"],
            "allow_attachments": [
              "TACZ:Scope_A",
              "#TACZ:Scopes",
              ":",
              3
            ]
          }
        """.trimIndent()

        val result = parser.parseGunDataJson(json, "test:allow-attachments")
        val gunData = result.gunData

        assertNotNull(gunData)
        assertEquals(setOf("tacz:scope_a", "#tacz:scopes"), gunData?.allowAttachments)
        assertTrue(result.report.allIssues().any {
            it.code == "INVALID_FIELD_VALUE" && it.field == "allow_attachments[2]"
        })
        assertTrue(result.report.allIssues().any {
            it.code == "INVALID_FIELD_TYPE" && it.field == "allow_attachments[3]"
        })
    }
}
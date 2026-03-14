package com.tacz.legacy.client.resource.pojo.display.gun

import com.google.gson.GsonBuilder
import com.tacz.legacy.client.resource.serialize.Vector3fSerializer
import net.minecraft.util.ResourceLocation
import org.joml.Vector3f
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for GunDisplay POJO deserialization and texture path expansion.
 * No Minecraft runtime needed — uses ResourceLocation directly.
 */
class GunDisplayParsingTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java,
            com.google.gson.JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) })
        .registerTypeAdapter(Vector3f::class.java, Vector3fSerializer())
        .create()

    @Test
    fun `parse minimal gun display JSON`() {
        val json = """
        {
          "model": "tacz:gun/model/ak47_geo",
          "texture": "tacz:gun/uv/ak47",
          "iron_zoom": 1.5,
          "show_crosshair": true
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        assertNotNull(display)
        assertEquals(ResourceLocation("tacz", "gun/model/ak47_geo"), display.modelLocation)
        assertEquals(ResourceLocation("tacz", "gun/uv/ak47"), display.modelTexture)
        assertEquals(1.5f, display.ironZoom, 0.001f)
        assertTrue(display.isShowCrosshair)
    }

    @Test
    fun `init expands texture paths`() {
        val json = """
        {
          "model": "tacz:gun/model/m4_geo",
          "texture": "tacz:gun/uv/m4",
          "hud": "tacz:gun/hud/m4",
          "hud_empty": "tacz:gun/hud/m4_empty",
          "slot": "tacz:gun/slot/m4"
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        display.init()

        assertEquals(
            "Texture should be expanded to textures/<path>.png",
            ResourceLocation("tacz", "textures/gun/uv/m4.png"),
            display.modelTexture
        )
        assertEquals(
            ResourceLocation("tacz", "textures/gun/hud/m4.png"),
            display.hudTextureLocation
        )
        assertEquals(
            ResourceLocation("tacz", "textures/gun/hud/m4_empty.png"),
            display.hudEmptyTextureLocation
        )
        assertEquals(
            ResourceLocation("tacz", "textures/gun/slot/m4.png"),
            display.slotTextureLocation
        )
    }

    @Test
    fun `parse gun display with sounds map`() {
        val json = """
        {
          "model": "tacz:gun/model/test_geo",
          "texture": "tacz:gun/uv/test",
          "sounds": {
            "shoot": "tacz:gun/sound/test_shoot",
            "reload_norm": "tacz:gun/sound/test_reload"
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        assertNotNull(display.sounds)
        assertEquals(2, display.sounds!!.size)
        assertEquals(ResourceLocation("tacz", "gun/sound/test_shoot"), display.sounds!!["shoot"])
        assertEquals(ResourceLocation("tacz", "gun/sound/test_reload"), display.sounds!!["reload_norm"])
    }

    @Test
    fun `parse gun display with transform`() {
        val json = """
        {
          "model": "tacz:gun/model/test_geo",
          "texture": "tacz:gun/uv/test",
          "transform": {
            "scale": {
              "thirdperson": [0.5, 0.5, 0.5],
              "ground": [1.0, 1.0, 1.0]
            }
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        assertNotNull(display.transform)
        assertNotNull(display.transform!!.scale)

        val scale = display.transform!!.scale
        assertNotNull(scale.thirdPerson)
        assertEquals(0.5f, scale.thirdPerson!!.x, 0.001f)
        assertEquals(0.5f, scale.thirdPerson!!.y, 0.001f)
        assertNotNull(scale.ground)
        assertEquals(1.0f, scale.ground!!.x, 0.001f)
    }

    @Test
    fun `parse gun display with animation and state machine`() {
        val json = """
        {
          "model": "tacz:gun/model/test_geo",
          "texture": "tacz:gun/uv/test",
          "animation": "tacz:gun/anim/test_anim",
          "state_machine": "tacz:gun/state/test_state",
          "third_person_animation": "rifle"
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        assertEquals(ResourceLocation("tacz", "gun/anim/test_anim"), display.animationLocation)
        assertEquals(ResourceLocation("tacz", "gun/state/test_state"), display.stateMachineLocation)
        assertEquals("rifle", display.thirdPersonAnimation)
    }

    @Test
    fun `parse gun display keeps animation fallback and render metadata`() {
        val json = """
        {
          "model": "tacz:gun/model/ak47_geo",
          "texture": "tacz:gun/uv/ak47",
          "animation": "tacz:ak47",
          "state_machine": "tacz:ak47_state_machine",
          "use_default_animation": "rifle",
          "default_animation": "tacz:common/rifle_default",
          "player_animator_3rd": "tacz:rifle_default.player_animation",
          "3rd_fixed_hand": true,
          "muzzle_flash": {
            "texture": "tacz:flash/common_muzzle_flash",
            "scale": 0.75
          },
          "shell": {
            "initial_velocity": [5, 2, 1],
            "random_velocity": [1, 1, 0.25],
            "acceleration": [0.0, -10, 0.0],
            "angular_velocity": [360, -1200, 90],
            "living_time": 1.0
          },
          "ammo": {
            "tracer_color": "#FF8888",
            "particle": {
              "name": "campfire_signal_smoke",
              "delta": [0, 0, 0],
              "speed": 0,
              "life_time": 50,
              "count": 5
            }
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        display.init()

        assertEquals(DefaultAnimationType.RIFLE, display.defaultAnimationType)
        assertEquals(ResourceLocation("tacz", "common/rifle_default"), display.defaultAnimation)
        assertEquals(ResourceLocation("tacz", "rifle_default.player_animation"), display.playerAnimator3rd)
        assertTrue(display.is3rdFixedHand())

        assertNotNull(display.muzzleFlash)
        assertEquals(ResourceLocation("tacz", "textures/flash/common_muzzle_flash.png"), display.muzzleFlash!!.texture)
        assertEquals(0.75f, display.muzzleFlash!!.scale, 0.001f)

        assertNotNull(display.shellEjection)
        assertEquals(5.0f, display.shellEjection!!.initialVelocity.x, 0.001f)
        assertEquals(-10.0f, display.shellEjection!!.acceleration.y, 0.001f)
        assertEquals(1.0f, display.shellEjection!!.livingTime, 0.001f)

        assertNotNull(display.gunAmmo)
        assertEquals("#FF8888", display.gunAmmo!!.tracerColor)
        assertNotNull(display.gunAmmo!!.particle)
        assertEquals("campfire_signal_smoke", display.gunAmmo!!.particle!!.name)
        assertEquals(50, display.gunAmmo!!.particle!!.lifeTime)
        assertEquals(5, display.gunAmmo!!.particle!!.count)
    }

    @Test
    fun `parse gun display laser config with defaults`() {
        val json = """
        {
          "model": "tacz:gun/model/minigun_geo",
          "texture": "tacz:gun/uv/minigun",
          "laser": {
            "default_color": "0x00FF00",
            "can_edit": true,
            "length": 10,
            "width": 0.008
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        assertNotNull(display.laserConfig)
        assertEquals(0x00FF00, display.laserConfig!!.defaultColor)
        assertTrue(display.laserConfig!!.canEdit())
        assertEquals(10, display.laserConfig!!.length)
        assertEquals(0.008f, display.laserConfig!!.width, 0.0001f)
        assertEquals(2.0f, display.laserConfig!!.lengthThird, 0.0001f)
        assertEquals(0.008f, display.laserConfig!!.widthThird, 0.0001f)
    }

    @Test
    fun `defaults when fields are absent`() {
        val json = """
        {
          "model": "tacz:gun/model/test_geo",
          "texture": "tacz:gun/uv/test"
        }
        """.trimIndent()

        val display = gson.fromJson(json, GunDisplay::class.java)
        assertEquals("default", display.modelType)
        assertEquals(1.2f, display.ironZoom, 0.001f)
        assertEquals(70f, display.zoomModelFov, 0.001f)
        assertFalse(display.isShowCrosshair)
        assertNull(display.animationLocation)
        assertNull(display.defaultAnimationType)
        assertNull(display.defaultAnimation)
        assertNull(display.playerAnimator3rd)
        assertNull(display.sounds)
        assertNull(display.transform)
        assertNull(display.gunLod)
        assertNull(display.shellEjection)
        assertNull(display.gunAmmo)
        assertNull(display.muzzleFlash)
    }

    @Test
    fun `ammo_count_style defaults to NORMAL when absent`() {
        val json = """
        {
          "model": "tacz:gun/model/ak47_geo",
          "texture": "tacz:gun/uv/ak47"
        }
        """.trimIndent()
        val display = gson.fromJson(json, GunDisplay::class.java)
        assertEquals(AmmoCountStyle.NORMAL, display.ammoCountStyle)
    }

    @Test
    fun `ammo_count_style parses normal`() {
        val json = """
        {
          "model": "tacz:gun/model/ak47_geo",
          "texture": "tacz:gun/uv/ak47",
          "ammo_count_style": "normal"
        }
        """.trimIndent()
        val display = gson.fromJson(json, GunDisplay::class.java)
        assertEquals(AmmoCountStyle.NORMAL, display.ammoCountStyle)
    }

    @Test
    fun `ammo_count_style parses percent`() {
        val json = """
        {
          "model": "tacz:gun/model/minigun_geo",
          "texture": "tacz:gun/uv/minigun",
          "ammo_count_style": "percent"
        }
        """.trimIndent()
        val display = gson.fromJson(json, GunDisplay::class.java)
        assertEquals(AmmoCountStyle.PERCENT, display.ammoCountStyle)
    }

    @Test
    fun `percent format produces expected output`() {
        val format = java.text.DecimalFormat("000%")
        // 20 out of 100 = 20%
        assertEquals("020%", format.format(20f / 100f))
        // 50 out of 200 = 25%
        assertEquals("025%", format.format(50f / 200f))
        // full ammo = 100%
        assertEquals("100%", format.format(80f / 80f))
        // 0 out of 80 = 0%
        assertEquals("000%", format.format(0f / 80f))
    }
}

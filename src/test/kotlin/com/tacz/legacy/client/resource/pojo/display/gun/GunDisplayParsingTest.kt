package com.tacz.legacy.client.resource.pojo.display.gun

import com.google.gson.GsonBuilder
import com.tacz.legacy.client.resource.serialize.Vector3fSerializer
import com.tacz.legacy.common.resource.TACZJson
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
        assertNull(display.sounds)
        assertNull(display.transform)
        assertNull(display.gunLod)
    }
}

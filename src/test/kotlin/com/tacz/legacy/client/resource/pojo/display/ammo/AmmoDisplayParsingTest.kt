package com.tacz.legacy.client.resource.pojo.display.ammo

import com.google.gson.GsonBuilder
import com.tacz.legacy.client.resource.serialize.Vector3fSerializer
import net.minecraft.util.ResourceLocation
import org.joml.Vector3f
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AmmoDisplay POJO deserialization and texture path expansion.
 */
class AmmoDisplayParsingTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java,
            com.google.gson.JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) })
        .registerTypeAdapter(Vector3f::class.java, Vector3fSerializer())
        .create()

    @Test
    fun `parse minimal ammo display JSON`() {
        val json = """
        {
          "model": "tacz:ammo/model/9mm_geo",
          "texture": "tacz:ammo/uv/9mm"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AmmoDisplay::class.java)
        assertNotNull(display)
        assertEquals(ResourceLocation("tacz", "ammo/model/9mm_geo"), display.modelLocation)
        assertEquals(ResourceLocation("tacz", "ammo/uv/9mm"), display.modelTexture)
    }

    @Test
    fun `init expands texture paths`() {
        val json = """
        {
          "model": "tacz:ammo/model/9mm_geo",
          "texture": "tacz:ammo/uv/9mm",
          "slot": "tacz:ammo/slot/9mm"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AmmoDisplay::class.java)
        display.init()

        assertEquals(
            ResourceLocation("tacz", "textures/ammo/uv/9mm.png"),
            display.modelTexture
        )
        assertEquals(
            ResourceLocation("tacz", "textures/ammo/slot/9mm.png"),
            display.slotTextureLocation
        )
    }

    @Test
    fun `parse ammo display with transform`() {
        val json = """
        {
          "model": "tacz:ammo/model/test_geo",
          "texture": "tacz:ammo/uv/test",
          "transform": {
            "scale": {
              "thirdperson": [0.5, 0.5, 0.5],
              "ground": [0.3, 0.3, 0.3],
              "fixed": [0.4, 0.4, 0.4]
            }
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, AmmoDisplay::class.java)
        assertNotNull(display.transform)
        assertNotNull(display.transform!!.scale)
        assertEquals(0.5f, display.transform!!.scale!!.thirdPerson!!.x(), 0.001f)
    }

    @Test
    fun `parse ammo display with entity model`() {
        val json = """
        {
          "model": "tacz:ammo/model/test_geo",
          "texture": "tacz:ammo/uv/test",
          "entity": {
            "model": "tacz:ammo/entity/test_entity_geo",
            "texture": "tacz:ammo/entity_uv/test_entity"
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, AmmoDisplay::class.java)
        assertNotNull(display.ammoEntity)
        assertEquals(ResourceLocation("tacz", "ammo/entity/test_entity_geo"), display.ammoEntity!!.modelLocation)

        display.init()
        assertEquals(
            ResourceLocation("tacz", "textures/ammo/entity_uv/test_entity.png"),
            display.ammoEntity!!.modelTexture,
        )
    }

    @Test
    fun `parse ammo display with shell model`() {
        val json = """
        {
          "model": "tacz:ammo/model/test_geo",
          "texture": "tacz:ammo/uv/test",
          "shell": {
            "model": "tacz:shell/test_shell_geo",
            "texture": "tacz:shell/test_shell"
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, AmmoDisplay::class.java)
        assertNotNull(display.shellDisplay)
        assertEquals(ResourceLocation("tacz", "shell/test_shell_geo"), display.shellDisplay!!.modelLocation)

        display.init()
        assertEquals(
            ResourceLocation("tacz", "textures/shell/test_shell.png"),
            display.shellDisplay!!.modelTexture,
        )
    }

    @Test
    fun `default transform returns ammo defaults`() {
        val defaultTransform = AmmoTransform.getDefault()
        assertNotNull(defaultTransform)
        assertNotNull(defaultTransform.scale)
    }
}

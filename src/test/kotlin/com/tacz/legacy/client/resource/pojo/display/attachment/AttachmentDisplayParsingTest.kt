package com.tacz.legacy.client.resource.pojo.display.attachment

import com.google.gson.GsonBuilder
import net.minecraft.util.ResourceLocation
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AttachmentDisplay POJO deserialization and texture path expansion.
 */
class AttachmentDisplayParsingTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java,
            com.google.gson.JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) })
        .create()

    @Test
    fun `parse minimal attachment display JSON`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        assertNotNull(display)
        assertEquals(ResourceLocation("tacz", "attachment/model/scope_geo"), display.model)
        assertEquals(ResourceLocation("tacz", "attachment/uv/scope"), display.texture)
    }

    @Test
    fun `init expands texture paths`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope",
          "slot": "tacz:attachment/slot/scope"
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        display.init()

        assertEquals(
            ResourceLocation("tacz", "textures/attachment/uv/scope.png"),
            display.texture
        )
        assertEquals(
            ResourceLocation("tacz", "textures/attachment/slot/scope.png"),
            display.slotTextureLocation
        )
    }

    @Test
    fun `parse attachment display with scope fields`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope4x_geo",
          "texture": "tacz:attachment/uv/scope4x",
          "scope": true,
          "fov": 45.0,
          "zoom": [4.0, 8.0]
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        assertTrue(display.isScope)
        assertEquals(45.0f, display.fov, 0.001f)
        assertNotNull(display.zoom)
        assertEquals(2, display.zoom!!.size)
        assertEquals(4.0f, display.zoom!![0], 0.001f)
    }

    @Test
    fun `parse attachment display with lod`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope",
          "lod": {
            "model": "tacz:attachment/model/scope_lod_geo",
            "texture": "tacz:attachment/uv/scope_lod"
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        assertNotNull(display.attachmentLod)
        assertEquals(ResourceLocation("tacz", "attachment/model/scope_lod_geo"), display.attachmentLod!!.modelLocation)
        assertEquals(ResourceLocation("tacz", "attachment/uv/scope_lod"), display.attachmentLod!!.modelTexture)
    }

    @Test
    fun `init expands lod texture path`() {
        val json = """
        {
          "model": "tacz:attachment/model/scope_geo",
          "texture": "tacz:attachment/uv/scope",
          "lod": {
            "model": "tacz:attachment/model/scope_lod_geo",
            "texture": "tacz:attachment/uv/scope_lod"
          }
        }
        """.trimIndent()

        val display = gson.fromJson(json, AttachmentDisplay::class.java)
        display.init()

        assertEquals(
            ResourceLocation("tacz", "textures/attachment/uv/scope_lod.png"),
            display.attachmentLod!!.modelTexture
        )
    }
}

package com.tacz.legacy.client.resource.pojo.display.block

import com.google.gson.GsonBuilder
import net.minecraft.util.ResourceLocation
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for BlockDisplay POJO deserialization and texture path expansion.
 */
class BlockDisplayParsingTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java,
            com.google.gson.JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) })
        .create()

    @Test
    fun `parse minimal block display JSON`() {
        val json = """
        {
          "model": "tacz:block/model/gunsmith_table_geo",
          "texture": "tacz:block/uv/gunsmith_table"
        }
        """.trimIndent()

        val display = gson.fromJson(json, BlockDisplay::class.java)
        assertNotNull(display)
        assertEquals(ResourceLocation("tacz", "block/model/gunsmith_table_geo"), display.modelLocation)
        assertEquals(ResourceLocation("tacz", "block/uv/gunsmith_table"), display.modelTexture)
    }

    @Test
    fun `init expands texture paths`() {
        val json = """
        {
          "model": "tacz:block/model/table_geo",
          "texture": "tacz:block/uv/table"
        }
        """.trimIndent()

        val display = gson.fromJson(json, BlockDisplay::class.java)
        display.init()

        assertEquals(
            ResourceLocation("tacz", "textures/block/uv/table.png"),
            display.modelTexture
        )
    }
}

package com.tacz.legacy.client.model.bedrock

import com.google.gson.GsonBuilder
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion
import com.tacz.legacy.client.resource.pojo.model.CubesItem
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for bedrock model POJO parsing and BedrockModel geometry loading.
 * These tests do not require a Minecraft runtime — they only exercise
 * JSON deserialization and geometry construction.
 */
class BedrockModelParsingTest {

    private val modelGson = GsonBuilder()
        .registerTypeAdapter(CubesItem::class.java, CubesItem.Deserializer())
        .create()

    @Test
    fun `parse new format bedrock model POJO`() {
        val json = """
        {
          "format_version": "1.12.0",
          "minecraft:geometry": [
            {
              "description": {
                "identifier": "geometry.test_gun",
                "texture_width": 128,
                "texture_height": 128,
                "visible_bounds_width": 2.0,
                "visible_bounds_height": 1.5,
                "visible_bounds_offset": [0.0, 0.25, 0.0]
              },
              "bones": [
                {
                  "name": "root",
                  "pivot": [0, 0, 0],
                  "cubes": [
                    {
                      "origin": [-4, 0, -4],
                      "size": [8, 2, 8],
                      "uv": [0, 0]
                    }
                  ]
                },
                {
                  "name": "barrel",
                  "parent": "root",
                  "pivot": [0, 2, 0],
                  "rotation": [0, 0, 0],
                  "cubes": [
                    {
                      "origin": [-1, 2, -1],
                      "size": [2, 10, 2],
                      "uv": [32, 0]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val pojo = modelGson.fromJson(json, BedrockModelPOJO::class.java)
        assertNotNull("POJO should not be null", pojo)
        assertEquals("1.12.0", pojo.formatVersion)
        assertTrue(BedrockVersion.isNewVersion(pojo))
        assertFalse(BedrockVersion.isLegacyVersion(pojo))

        val geo = pojo.geometryModelNew
        assertNotNull("New geometry model should not be null", geo)
        assertNotNull("Bones should not be null", geo!!.bones)
        assertEquals(2, geo.bones!!.size)
        assertEquals("root", geo.bones!![0].name)
        assertEquals("barrel", geo.bones!![1].name)
        assertEquals("root", geo.bones!![1].parent)

        val desc = geo.description
        assertNotNull(desc)
        assertEquals(128, desc.textureWidth)
        assertEquals(128, desc.textureHeight)
    }

    @Test
    fun `parse legacy format bedrock model POJO`() {
        val json = """
        {
          "format_version": "1.10.0",
          "geometry.model": {
            "texturewidth": 64,
            "textureheight": 64,
            "visible_bounds_width": 1.5,
            "visible_bounds_height": 1.0,
            "visible_bounds_offset": [0, 0.5, 0],
            "bones": [
              {
                "name": "body",
                "pivot": [0, 12, 0],
                "cubes": [
                  {
                    "origin": [-2, 10, -2],
                    "size": [4, 4, 4],
                    "uv": [0, 0]
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

        val pojo = modelGson.fromJson(json, BedrockModelPOJO::class.java)
        assertNotNull(pojo)
        assertEquals("1.10.0", pojo.formatVersion)
        assertTrue(BedrockVersion.isLegacyVersion(pojo))

        val geo = pojo.geometryModelLegacy
        assertNotNull(geo)
        assertNotNull(geo!!.bones)
        assertEquals(1, geo.bones!!.size)
        assertEquals("body", geo.bones!![0].name)
        assertEquals(64, geo.textureWidth)
        assertEquals(64, geo.textureHeight)
    }

    @Test
    fun `parse cube with per-face UVs`() {
        val json = """
        {
          "format_version": "1.12.0",
          "minecraft:geometry": [
            {
              "description": {
                "identifier": "geometry.perface",
                "texture_width": 32,
                "texture_height": 32,
                "visible_bounds_width": 1.0,
                "visible_bounds_height": 1.0,
                "visible_bounds_offset": [0, 0, 0]
              },
              "bones": [
                {
                  "name": "root",
                  "pivot": [0, 0, 0],
                  "cubes": [
                    {
                      "origin": [0, 0, 0],
                      "size": [4, 4, 4],
                      "uv": {
                        "north": { "uv": [0, 0], "uv_size": [4, 4] },
                        "south": { "uv": [4, 0], "uv_size": [4, 4] },
                        "east": { "uv": [8, 0], "uv_size": [4, 4] },
                        "west": { "uv": [12, 0], "uv_size": [4, 4] },
                        "up": { "uv": [16, 0], "uv_size": [4, 4] },
                        "down": { "uv": [20, 0], "uv_size": [4, 4] }
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val pojo = modelGson.fromJson(json, BedrockModelPOJO::class.java)
        val geo = pojo.geometryModelNew!!
        val cube = geo.bones!![0].cubes!![0]
        assertNotNull("Per-face UV should be parsed", cube.faceUv)
        assertNull("Standard UV should be null for per-face", cube.uv)
    }

    @Test
    fun `BedrockVersion fromPojo returns correct version`() {
        val newJson = """{"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"g","texture_width":1,"texture_height":1,"visible_bounds_width":1,"visible_bounds_height":1,"visible_bounds_offset":[0,0,0]},"bones":[]}]}"""
        val legacyJson = """{"format_version":"1.10.0","geometry.test":{"texturewidth":1,"textureheight":1,"visible_bounds_width":1,"visible_bounds_height":1,"visible_bounds_offset":[0,0,0],"bones":[]}}"""

        val newPojo = modelGson.fromJson(newJson, BedrockModelPOJO::class.java)
        val legacyPojo = modelGson.fromJson(legacyJson, BedrockModelPOJO::class.java)

        assertEquals(BedrockVersion.NEW, BedrockVersion.fromPojo(newPojo))
        assertEquals(BedrockVersion.LEGACY, BedrockVersion.fromPojo(legacyPojo))
    }
}

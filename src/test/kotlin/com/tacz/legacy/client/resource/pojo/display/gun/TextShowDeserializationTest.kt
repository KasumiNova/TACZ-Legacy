package com.tacz.legacy.client.resource.pojo.display.gun

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TextShowDeserializationTest {
    private val gson: Gson = GsonBuilder().create()

    @Test
    fun `deserialize TextShow with all fields`() {
        val json = """{
            "scale": 0.8,
            "align": "left",
            "shadow": true,
            "color": "#00FF00",
            "light": 12,
            "text": "tacz.text_show.ammo_count"
        }"""
        val textShow = gson.fromJson(json, TextShow::class.java)
        assertEquals(0.8f, textShow.scale, 0.001f)
        assertEquals(Align.LEFT, textShow.align)
        assertTrue(textShow.isShadow)
        assertEquals("#00FF00", textShow.colorText)
        assertEquals(12, textShow.textLight)
        assertEquals("tacz.text_show.ammo_count", textShow.textKey)
        // colorInt is not yet resolved from colorText
        assertEquals(0xFFFFFF, textShow.colorInt)
    }

    @Test
    fun `deserialize TextShow with defaults`() {
        val textShow = gson.fromJson("{}", TextShow::class.java)
        assertEquals(1.0f, textShow.scale, 0.001f)
        assertEquals(Align.CENTER, textShow.align)
        assertFalse(textShow.isShadow)
        assertEquals("#FFFFFF", textShow.colorText)
        assertEquals(15, textShow.textLight)
        assertEquals("", textShow.textKey)
    }

    @Test
    fun `deserialize Align enum`() {
        assertEquals(Align.LEFT, gson.fromJson("\"left\"", Align::class.java))
        assertEquals(Align.CENTER, gson.fromJson("\"center\"", Align::class.java))
        assertEquals(Align.RIGHT, gson.fromJson("\"right\"", Align::class.java))
    }

    @Test
    fun `text_show map deserialization round trip`() {
        val json = """{
            "text_show": {
                "ammo_display": {
                    "scale": 1.5,
                    "align": "right",
                    "color": "#FF0000",
                    "text": "%ammo_count%"
                },
                "player_tag": {
                    "scale": 0.5,
                    "text": "%player_name%"
                }
            }
        }"""
        val obj = JsonParser().parse(json).asJsonObject
        val textShowObj = obj.getAsJsonObject("text_show")
        assertNotNull(textShowObj)
        assertEquals(2, textShowObj.size())

        val ammoDisplay = gson.fromJson(textShowObj.get("ammo_display"), TextShow::class.java)
        assertEquals(1.5f, ammoDisplay.scale, 0.001f)
        assertEquals(Align.RIGHT, ammoDisplay.align)
        assertEquals("#FF0000", ammoDisplay.colorText)
        assertEquals("%ammo_count%", ammoDisplay.textKey)

        val playerTag = gson.fromJson(textShowObj.get("player_tag"), TextShow::class.java)
        assertEquals(0.5f, playerTag.scale, 0.001f)
        assertEquals("%player_name%", playerTag.textKey)
    }

    @Test
    fun `colorInt can be set independently`() {
        val textShow = gson.fromJson("{\"color\": \"#00FF00\"}", TextShow::class.java)
        textShow.colorInt = 0x00FF00
        assertEquals(0x00FF00, textShow.colorInt)
    }
}

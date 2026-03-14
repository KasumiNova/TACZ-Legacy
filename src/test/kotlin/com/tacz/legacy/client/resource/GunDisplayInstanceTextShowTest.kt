package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test

class GunDisplayInstanceTextShowTest {
    private val displayGson = GsonBuilder()
        .registerTypeAdapter(
            ResourceLocation::class.java,
            JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) },
        )
        .create()

    @Test
    fun `collectTextShowMap resolves colors and filters blank keys`() {
        val display = displayGson.fromJson(
            """
            {
              "model": "tacz:gun/model/test_geo",
              "texture": "tacz:gun/uv/test",
              "text_show": {
                "ammo_display": {
                  "scale": 0.75,
                  "align": "center",
                  "color": "#91FFF2",
                  "text": "%ammo_count%"
                },
                "": {
                  "color": "#FF0000",
                  "text": "ignored"
                }
              }
            }
            """.trimIndent(),
            GunDisplay::class.java,
        ).also { it.init() }

        val method = GunDisplayInstance::class.java.getDeclaredMethod("collectTextShowMap", Map::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val textShowMap = method.invoke(null, display.textShows) as Map<String, com.tacz.legacy.client.resource.pojo.display.gun.TextShow>

        assertEquals(1, textShowMap.size)
        assertFalse(textShowMap.containsKey(""))

        val textShow = textShowMap["ammo_display"]
        assertNotNull(textShow)
        assertEquals(0x91FFF2, textShow!!.colorInt)
    }
}
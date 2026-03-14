package com.tacz.legacy.client.resource.index

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.tacz.legacy.client.resource.pojo.display.attachment.AttachmentDisplay
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test

class ClientAttachmentIndexTextShowTest {
    private val displayGson = GsonBuilder()
        .registerTypeAdapter(
            ResourceLocation::class.java,
            JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) },
        )
        .create()

    @Test
    fun `collectTextShowMap resolves attachment colors and filters blank keys`() {
        val display = displayGson.fromJson(
            """
            {
              "model": "tacz:attachment/model/test_scope_geo",
              "texture": "tacz:attachment/uv/test_scope",
              "text_show": {
                "status_label": {
                  "scale": 0.5,
                  "color": "#44AAFF",
                  "text": "%player_name%"
                },
                "": {
                  "color": "#00FF00",
                  "text": "ignored"
                }
              }
            }
            """.trimIndent(),
            AttachmentDisplay::class.java,
        ).also { it.init() }

        val method = ClientAttachmentIndex::class.java.getDeclaredMethod("collectTextShowMap", Map::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val textShowMap = method.invoke(null, display.textShows) as Map<String, com.tacz.legacy.client.resource.pojo.display.gun.TextShow>

        assertEquals(1, textShowMap.size)
        assertFalse(textShowMap.containsKey(""))

        val textShow = textShowMap["status_label"]
        assertNotNull(textShow)
        assertEquals(0x44AAFF, textShow!!.colorInt)
    }
}
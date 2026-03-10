package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.tacz.legacy.sound.SoundManager
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class GunDisplayInstanceHitFeedbackSoundTest {
    private val gson = GsonBuilder()
        .registerTypeAdapter(
            ResourceLocation::class.java,
            JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) },
        )
        .create()

    @Test
    fun `gun display instance supplies default hit feedback sounds when missing`() {
        val display = gson.fromJson(
            """
            {
              "model": "tacz:gun/model/test_geo",
              "texture": "tacz:gun/uv/test",
              "sounds": {
                "shoot": "demo:test/shoot"
              }
            }
            """.trimIndent(),
            com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay::class.java,
        ).also { it.init() }

        val ctor = GunDisplayInstance::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        val instance = ctor.newInstance()
        val checkSounds = GunDisplayInstance::class.java.getDeclaredMethod(
            "checkSounds",
            com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay::class.java,
        )
        checkSounds.isAccessible = true
        checkSounds.invoke(instance, display)

        assertEquals(ResourceLocation("tacz", SoundManager.HEAD_HIT_SOUND), instance.getSound(SoundManager.HEAD_HIT_SOUND))
        assertEquals(ResourceLocation("tacz", SoundManager.FLESH_HIT_SOUND), instance.getSound(SoundManager.FLESH_HIT_SOUND))
        assertEquals(ResourceLocation("tacz", SoundManager.KILL_SOUND), instance.getSound(SoundManager.KILL_SOUND))
    }
}
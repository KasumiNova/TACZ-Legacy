package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.AnimationListenerSupplier
import com.tacz.legacy.api.client.animation.Animations
import com.tacz.legacy.api.client.animation.ObjectAnimation
import com.tacz.legacy.client.resource.pojo.animation.bedrock.AnimationKeyframes
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile
import com.tacz.legacy.client.resource.pojo.animation.bedrock.SoundEffectKeyframes
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.serialize.AnimationKeyframesSerializer
import com.tacz.legacy.client.resource.serialize.SoundEffectKeyframesSerializer
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GunDisplayInstanceDefaultAnimationTest {
    private val animationGson = GsonBuilder()
        .registerTypeAdapter(AnimationKeyframes::class.java, AnimationKeyframesSerializer())
        .registerTypeAdapter(SoundEffectKeyframes::class.java, SoundEffectKeyframesSerializer())
        .create()

    private val displayGson = GsonBuilder()
      .registerTypeAdapter(
        ResourceLocation::class.java,
        JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) },
      )
        .create()

    @Test
    fun `explicit default animation takes precedence over built in type fallback`() {
        val display = parseDisplay(
            """
            {
              "model": "tacz:test_model",
              "texture": "tacz:gun/uv/test",
              "animation": "tacz:test_animation",
              "use_default_animation": "rifle",
              "default_animation": "example:custom_default"
            }
            """.trimIndent(),
        )

        assertEquals(
            ResourceLocation("example", "custom_default"),
            GunDisplayInstance.resolveFallbackAnimationLocation(display),
        )
    }

    @Test
    fun `built in rifle fallback is selected when explicit default animation is absent`() {
        val display = parseDisplay(
            """
            {
              "model": "tacz:test_model",
              "texture": "tacz:gun/uv/test",
              "animation": "tacz:test_animation",
              "use_default_animation": "rifle"
            }
            """.trimIndent(),
        )

        assertEquals(
            ResourceLocation("tacz", "rifle_default"),
            GunDisplayInstance.resolveFallbackAnimationLocation(display),
        )
    }

    @Test
    fun `fallback prototype injection preserves existing named animations`() {
        val primary = parseAnimationFile(
            """
            {
              "format_version": "1.8.0",
              "animations": {
                "draw": {
                  "animation_length": 0.1,
                  "bones": {
                    "root": {
                      "position": {
                        "0.0": {"post": [0, 0, 0]},
                        "0.1": {"post": [16, 0, 0]}
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val fallback = parseAnimationFile(
            """
            {
              "format_version": "1.8.0",
              "animations": {
                "draw": {
                  "animation_length": 0.75,
                  "bones": {
                    "root": {
                      "position": {
                        "0.0": {"post": [0, 0, 0]},
                        "0.75": {"post": [4, 0, 0]}
                      }
                    }
                  }
                },
                "idle": {
                  "loop": true,
                  "animation_length": 1.0,
                  "bones": {
                    "root": {
                      "position": {
                        "0.0": {"post": [0, 0, 0]},
                        "1.0": {"post": [1, 0, 0]}
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val controller = AnimationController(
            Animations.createAnimationFromBedrock(primary),
            AnimationListenerSupplier { _, _ -> null },
        )

        GunDisplayInstance.provideAnimationPrototypesIfAbsent(controller, fallback)

        assertTrue(controller.containPrototype("draw"))
        assertTrue(controller.containPrototype("idle"))

        val prototypesField = AnimationController::class.java.getDeclaredField("prototypes")
        prototypesField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val prototypes = prototypesField.get(controller) as Map<String, ObjectAnimation>

        assertEquals(0.1f, prototypes.getValue("draw").maxEndTimeS, 1.0e-6f)
        assertEquals(1.0f, prototypes.getValue("idle").maxEndTimeS, 1.0e-6f)
    }

    private fun parseDisplay(json: String): GunDisplay =
        displayGson.fromJson(json, GunDisplay::class.java).also { it.init() }

    private fun parseAnimationFile(json: String): BedrockAnimationFile =
        animationGson.fromJson(json, BedrockAnimationFile::class.java)
}

package com.tacz.legacy.client.resource

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.tacz.legacy.client.resource.pojo.display.ammo.AmmoDisplay
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import net.minecraft.util.ResourceLocation
import org.junit.Assert.assertTrue
import org.junit.Test

class TACZClientAssetLoadPlanTest {
    private val displayGson = GsonBuilder()
        .registerTypeAdapter(
            ResourceLocation::class.java,
            JsonDeserializer<ResourceLocation> { json, _, _ -> ResourceLocation(json.asString) },
        )
        .create()

    @Test
    fun `gun muzzle flash texture is included in asset load plan`() {
        val display = parseDisplay(
            """
            {
              "model": "tacz:test_model",
              "texture": "tacz:gun/uv/test",
              "animation": "tacz:test_animation",
              "muzzle_flash": {
                "texture": "tacz:flash/common_muzzle_flash",
                "scale": 0.6
              }
            }
            """.trimIndent(),
        )

        val loadPlan = TACZClientAssetManager.buildAssetLoadPlan(
            gunDisplays = listOf(display),
            ammoDisplays = emptyList(),
            attachmentDisplays = emptyList(),
            blockDisplays = emptyList(),
        )

        assertTrue(
            "muzzle flash texture should be queued for loading",
            loadPlan.textures.contains(ResourceLocation("tacz", "textures/flash/common_muzzle_flash.png")),
        )
    }

    @Test
    fun `ammo entity model and texture are included in asset load plan`() {
        val display = parseAmmoDisplay(
            """
            {
              "model": "tacz:ammo/test_model",
              "texture": "tacz:ammo/uv/test",
              "entity": {
                "model": "tacz:ammo/test_entity_model",
                "texture": "tacz:ammo/entity_uv/test"
              }
            }
            """.trimIndent(),
        )

        val loadPlan = TACZClientAssetManager.buildAssetLoadPlan(
            gunDisplays = emptyList(),
            ammoDisplays = listOf(display),
            attachmentDisplays = emptyList(),
            blockDisplays = emptyList(),
        )

        assertTrue(
            "ammo entity model should be queued for loading",
            loadPlan.models.contains(ResourceLocation("tacz", "ammo/test_entity_model")),
        )
        assertTrue(
            "ammo entity texture should be queued for loading",
            loadPlan.textures.contains(ResourceLocation("tacz", "textures/ammo/entity_uv/test.png")),
        )
    }

    @Test
    fun `ammo shell model and texture are included in asset load plan`() {
        val display = parseAmmoDisplay(
            """
            {
              "model": "tacz:ammo/test_model",
              "texture": "tacz:ammo/uv/test",
              "shell": {
                "model": "tacz:shell/test_shell_model",
                "texture": "tacz:shell/test_shell"
              }
            }
            """.trimIndent(),
        )

        val loadPlan = TACZClientAssetManager.buildAssetLoadPlan(
            gunDisplays = emptyList(),
            ammoDisplays = listOf(display),
            attachmentDisplays = emptyList(),
            blockDisplays = emptyList(),
        )

        assertTrue(
            "ammo shell model should be queued for loading",
            loadPlan.models.contains(ResourceLocation("tacz", "shell/test_shell_model")),
        )
        assertTrue(
            "ammo shell texture should be queued for loading",
            loadPlan.textures.contains(ResourceLocation("tacz", "textures/shell/test_shell.png")),
        )
    }

    private fun parseDisplay(json: String): GunDisplay =
        displayGson.fromJson(json, GunDisplay::class.java).also { it.init() }

    private fun parseAmmoDisplay(json: String): AmmoDisplay =
        displayGson.fromJson(json, AmmoDisplay::class.java).also { it.init() }
}
package com.tacz.legacy.client.render.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponVisualSampleRegistryTest {

    @Test
    public fun `resolve should return ak47 sample definition`() {
        val definition = WeaponVisualSampleRegistry.resolve("AK47")

        assertNotNull(definition)
        assertEquals("ak47", definition?.gunId)
        assertTrue(definition?.firstPersonModelPath?.contains("ak47_fp") == true)
        assertTrue(definition?.reloadAnimationPath?.contains("ak47/reload") == true)
        assertTrue(definition?.hudTexturePath?.contains("textures/hud/weapon/ak47") == true)
        assertTrue(definition?.hudEmptyTexturePath?.contains("ak47_empty") == true)
    }

    @Test
    public fun `resolve should prefer display definition paths when available`() {
        val definition = WeaponVisualSampleRegistry.resolve(
            gunId = "AK47",
            displayDefinition = GunDisplayDefinition(
                sourceId = "sample_pack/assets/tacz/display/guns/ak47_display.json",
                gunId = "ak47",
                displayResource = "tacz:ak47_display",
                modelPath = "assets/tacz/geo_models/gun/ak47_geo.json",
                modelTexturePath = "assets/tacz/textures/gun/uv/ak47.png",
                lodModelPath = "assets/tacz/geo_models/gun/lod/ak47.json",
                lodTexturePath = "assets/tacz/textures/gun/lod/ak47.png",
                slotTexturePath = "assets/tacz/textures/gun/slot/ak47.png",
                animationPath = "assets/tacz/animations/ak47.animation.json",
                stateMachinePath = "assets/tacz/scripts/ak47_state_machine.lua",
                playerAnimator3rdPath = "assets/tacz/player_animator/rifle_default.player_animation.json",
                thirdPersonAnimation = "m16",
                modelParseSucceeded = true,
                modelBoneCount = 12,
                modelCubeCount = 48,
                animationParseSucceeded = true,
                animationClipCount = 10,
                stateMachineResolved = true,
                playerAnimatorResolved = true,
                hudTexturePath = "assets/tacz/textures/gun/hud/ak47.png",
                hudEmptyTexturePath = "assets/tacz/textures/gun/hud/ak47_empty.png",
                showCrosshair = true
            )
        )

        assertNotNull(definition)
        assertEquals("assets/tacz/geo_models/gun/ak47_geo.json", definition?.firstPersonModelPath)
        assertEquals("assets/tacz/geo_models/gun/lod/ak47.json", definition?.thirdPersonModelPath)
        assertEquals("assets/tacz/animations/ak47.animation.json", definition?.reloadAnimationPath)
        assertEquals("assets/tacz/textures/gun/hud/ak47.png", definition?.hudTexturePath)
    }

    @Test
    public fun `resolve should return null for unknown or blank ids`() {
        assertNull(WeaponVisualSampleRegistry.resolve(""))
        assertNull(WeaponVisualSampleRegistry.resolve("unknown_gun"))
        assertNull(WeaponVisualSampleRegistry.resolve(null))
    }

    @Test
    public fun `registered gun ids should include ak47`() {
        val ids = WeaponVisualSampleRegistry.registeredGunIds()
        assertTrue(ids.contains("ak47"))
    }

}

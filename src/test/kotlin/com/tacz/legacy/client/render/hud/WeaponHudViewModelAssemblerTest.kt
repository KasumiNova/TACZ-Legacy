package com.tacz.legacy.client.render.hud

import com.tacz.legacy.client.render.weapon.WeaponVisualSampleDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.weapon.WeaponBallistics
import com.tacz.legacy.common.application.weapon.WeaponDefinition
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponSpec
import com.tacz.legacy.common.domain.weapon.WeaponState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponHudViewModelAssemblerTest {

    private val assembler: WeaponHudViewModelAssembler = WeaponHudViewModelAssembler()

    @Test
    public fun `assemble should use session ammo and low ammo color`() {
        val model = assembler.assemble(
            fallbackGunId = "ak47",
            definition = sampleDefinition(),
            sessionDebugSnapshot = WeaponSessionDebugSnapshot(
                sourceId = "ak47.json",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 5,
                    ammoReserve = 40
                )
            ),
            visualSample = sampleVisual(),
            uiConfig = WeaponHudUiConfig(crosshairType = WeaponCrosshairType.CROSS_1)
        )

        assertNotNull(model)
        assertEquals("ak47", model?.gunId)
        assertEquals("005", model?.currentAmmoText)
        assertEquals("0040", model?.reserveAmmoText)
        assertEquals(0xFF5555, model?.currentAmmoColor)
        assertEquals(0xAAAAAA, model?.reserveAmmoColor)
        assertEquals(WeaponFireMode.AUTO, model?.fireMode)
        assertEquals(WeaponCrosshairType.CROSS_1, model?.crosshairType)
        assertTrue(model?.showCrosshair == true)
        assertEquals(0f, model?.reloadProgress)
        assertEquals(0f, model?.cooldownProgress)
        assertEquals(0, model?.totalShotsFired)
        assertTrue(model?.hudTexturePath?.contains("ak47") == true)
        assertEquals(null, model?.hudTextureSourceId)
    }

    @Test
    public fun `assemble should hide crosshair while reloading`() {
        val model = assembler.assemble(
            fallbackGunId = "ak47",
            definition = sampleDefinition(),
            sessionDebugSnapshot = WeaponSessionDebugSnapshot(
                sourceId = "ak47.json",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    state = WeaponState.RELOADING,
                    ammoInMagazine = 12,
                    ammoReserve = 90,
                    reloadTicksRemaining = 15
                )
            ),
            visualSample = sampleVisual()
        )

        assertNotNull(model)
        assertFalse(model?.showCrosshair == true)
        assertEquals(15f / 45f, model?.reloadProgress ?: 0f, 0.0001f)
    }

    @Test
    public fun `assemble should prefer runtime display hud textures over sample textures`() {
        val model = assembler.assemble(
            fallbackGunId = "ak47",
            definition = sampleDefinition(),
            sessionDebugSnapshot = WeaponSessionDebugSnapshot(
                sourceId = "ak47.json",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 12,
                    ammoReserve = 99
                )
            ),
            displayDefinition = GunDisplayDefinition(
                sourceId = "sample_pack/assets/tacz/display/guns/ak47_display.json",
                gunId = "ak47",
                displayResource = "tacz:ak47_display",
                modelPath = null,
                modelTexturePath = null,
                lodModelPath = null,
                lodTexturePath = null,
                slotTexturePath = null,
                animationPath = null,
                stateMachinePath = null,
                playerAnimator3rdPath = null,
                thirdPersonAnimation = null,
                modelParseSucceeded = false,
                modelBoneCount = null,
                modelCubeCount = null,
                animationParseSucceeded = false,
                animationClipCount = null,
                stateMachineResolved = false,
                playerAnimatorResolved = false,
                hudTexturePath = "assets/tacz/textures/gun/hud/ak47_runtime.png",
                hudEmptyTexturePath = "assets/tacz/textures/gun/hud/ak47_runtime_empty.png",
                showCrosshair = true
            ),
            visualSample = sampleVisual()
        )

        assertNotNull(model)
        assertEquals("assets/tacz/textures/gun/hud/ak47_runtime.png", model?.hudTexturePath)
        assertEquals("assets/tacz/textures/gun/hud/ak47_runtime_empty.png", model?.hudEmptyTexturePath)
        assertEquals("sample_pack/assets/tacz/display/guns/ak47_display.json", model?.hudTextureSourceId)
    }

    @Test
    public fun `assemble should disable crosshair when display definition says so`() {
        val model = assembler.assemble(
            fallbackGunId = "ak47",
            definition = sampleDefinition(),
            sessionDebugSnapshot = WeaponSessionDebugSnapshot(
                sourceId = "ak47.json",
                gunId = "ak47",
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 12,
                    ammoReserve = 99
                )
            ),
            displayDefinition = GunDisplayDefinition(
                sourceId = "sample_pack/assets/tacz/display/guns/ak47_display.json",
                gunId = "ak47",
                displayResource = "tacz:ak47_display",
                modelPath = null,
                modelTexturePath = null,
                lodModelPath = null,
                lodTexturePath = null,
                slotTexturePath = null,
                animationPath = null,
                stateMachinePath = null,
                playerAnimator3rdPath = null,
                thirdPersonAnimation = null,
                modelParseSucceeded = false,
                modelBoneCount = null,
                modelCubeCount = null,
                animationParseSucceeded = false,
                animationClipCount = null,
                stateMachineResolved = false,
                playerAnimatorResolved = false,
                hudTexturePath = "assets/tacz/textures/gun/hud/ak47_runtime.png",
                hudEmptyTexturePath = "assets/tacz/textures/gun/hud/ak47_runtime_empty.png",
                showCrosshair = false
            ),
            visualSample = sampleVisual()
        )

        assertNotNull(model)
        assertFalse(model?.showCrosshair == true)
    }

    private fun sampleDefinition(): WeaponDefinition =
        WeaponDefinition(
            sourceId = "ak47.json",
            gunId = "ak47",
            ammoId = "tacz:7_62x39",
            spec = WeaponSpec(
                magazineSize = 30,
                roundsPerMinute = 700,
                reloadTicks = 45,
                fireMode = WeaponFireMode.AUTO
            ),
            ballistics = WeaponBallistics(
                speed = 5.4f,
                gravity = 0.02f,
                damage = 5.8f,
                lifetimeTicks = 180,
                pierce = 2,
                pelletCount = 1
            )
        )

    private fun sampleVisual(): WeaponVisualSampleDefinition =
        WeaponVisualSampleDefinition(
            gunId = "ak47",
            firstPersonModelPath = "assets/tacz/models/item/ak47_fp.json",
            thirdPersonModelPath = "assets/tacz/models/item/ak47_tp.json",
            idleAnimationPath = "assets/tacz/animations/weapon/ak47/idle.json",
            fireAnimationPath = "assets/tacz/animations/weapon/ak47/fire.json",
            reloadAnimationPath = "assets/tacz/animations/weapon/ak47/reload.json",
            hudTexturePath = "assets/tacz/textures/hud/weapon/ak47.png",
            hudEmptyTexturePath = "assets/tacz/textures/hud/weapon/ak47_empty.png"
        )

}

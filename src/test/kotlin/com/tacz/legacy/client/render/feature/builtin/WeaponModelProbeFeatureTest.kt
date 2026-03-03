package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.core.RenderPipelineCoordinator
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.weapon.WeaponVisualSampleDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponModelProbeFeatureTest {

    @Test
    public fun `weapon model probe should queue and execute submit command when gate open`() {
        val renderedTextures = mutableListOf<String>()
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(
            WeaponModelProbeFeature(
                stateProvider = {
                    WeaponModelProbeState(
                        gunId = "ak47",
                        displayDefinition = sampleDisplayDefinition(modelParseSucceeded = true),
                        visualSample = sampleVisualSample()
                    )
                },
                previewRenderer = WeaponModelPreviewRenderer { textureAssetPath, _, _ ->
                    renderedTextures += textureAssetPath
                }
            )
        )
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)

        val context = sampleContext()

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)
        coordinator.runPhase(FramePhase.RENDER_TRANSLUCENT, context)
        coordinator.runPhase(FramePhase.POST_UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OVERLAY, context)

        assertEquals(true, context.diagnostics["weapon.model_probe.active"])
        assertEquals(true, context.diagnostics["weapon.model_probe.gate_open"])
        assertEquals("none", context.diagnostics["weapon.model_probe.blocked_reason"])
        assertEquals(1, context.diagnostics["weapon.model_submit.queued"])
        assertEquals(1, context.diagnostics["weapon.model_submit.executed"])
        assertTrue((context.diagnostics["weapon.model_submit.last_model_path"] as? String)?.contains("geo_models") == true)
        assertEquals(0, (context.diagnostics["weapon.model_preview.rendered"] as? Int) ?: 0)
        assertEquals("preview_disabled", context.diagnostics["weapon.model_preview.skipped_reason"])
        assertEquals(0, renderedTextures.size)
    }

    @Test
    public fun `weapon model probe should render preview when overlay is enabled`() {
        val renderedTextures = mutableListOf<String>()
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(
            WeaponModelProbeFeature(
                stateProvider = {
                    WeaponModelProbeState(
                        gunId = "ak47",
                        displayDefinition = sampleDisplayDefinition(modelParseSucceeded = true),
                        visualSample = sampleVisualSample()
                    )
                },
                previewRenderer = WeaponModelPreviewRenderer { textureAssetPath, _, _ ->
                    renderedTextures += textureAssetPath
                }
            )
        )
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)

        val context = sampleContext(
            config = RenderPipelineConfig(enableModelPreviewOverlay = true)
        )

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)
        coordinator.runPhase(FramePhase.RENDER_TRANSLUCENT, context)
        coordinator.runPhase(FramePhase.POST_UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OVERLAY, context)

        assertEquals(1, context.diagnostics["weapon.model_preview.rendered"])
        assertEquals(1, renderedTextures.size)
        assertTrue(renderedTextures.first().contains("textures/gun/hud/ak47"))
    }

    @Test
    public fun `weapon model probe should block submit when model parse failed`() {
        val renderedTextures = mutableListOf<String>()
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(
            WeaponModelProbeFeature(
                stateProvider = {
                    WeaponModelProbeState(
                        gunId = "ak47",
                        displayDefinition = sampleDisplayDefinition(modelParseSucceeded = false),
                        visualSample = sampleVisualSample()
                    )
                },
                previewRenderer = WeaponModelPreviewRenderer { textureAssetPath, _, _ ->
                    renderedTextures += textureAssetPath
                }
            )
        )
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)

        val context = sampleContext()

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)
        coordinator.runPhase(FramePhase.RENDER_TRANSLUCENT, context)
        coordinator.runPhase(FramePhase.POST_UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OVERLAY, context)

        assertEquals(true, context.diagnostics["weapon.model_probe.active"])
        assertEquals(false, context.diagnostics["weapon.model_probe.gate_open"])
        assertEquals("model_not_ready", context.diagnostics["weapon.model_probe.blocked_reason"])
        assertEquals(1, context.diagnostics["weapon.model_probe.blocked"])
        assertEquals(0, (context.diagnostics["weapon.model_submit.queued"] as? Int) ?: 0)
        assertEquals(0, (context.diagnostics["weapon.model_submit.executed"] as? Int) ?: 0)
        assertEquals(0, renderedTextures.size)
    }

    private fun sampleContext(config: RenderPipelineConfig = RenderPipelineConfig()): RenderContext =
        RenderContext(
            frameId = 1,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = config
        )

    private fun sampleDisplayDefinition(modelParseSucceeded: Boolean): GunDisplayDefinition =
        GunDisplayDefinition(
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
            modelParseSucceeded = modelParseSucceeded,
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

    private fun sampleVisualSample(): WeaponVisualSampleDefinition =
        WeaponVisualSampleDefinition(
            gunId = "ak47",
            firstPersonModelPath = "assets/tacz/geo_models/gun/ak47_geo.json",
            thirdPersonModelPath = "assets/tacz/geo_models/gun/lod/ak47.json",
            idleAnimationPath = "assets/tacz/animations/ak47.animation.json",
            fireAnimationPath = "assets/tacz/animations/ak47.animation.json",
            reloadAnimationPath = "assets/tacz/animations/ak47.animation.json",
            hudTexturePath = "assets/tacz/textures/gun/hud/ak47.png",
            hudEmptyTexturePath = "assets/tacz/textures/gun/hud/ak47_empty.png"
        )

}
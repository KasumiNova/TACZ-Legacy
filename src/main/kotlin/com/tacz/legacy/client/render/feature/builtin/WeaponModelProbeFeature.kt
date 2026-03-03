package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.draw.LambdaDrawCommand
import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.SimpleSubpass
import com.tacz.legacy.client.render.texture.TaczTextureResourceResolver
import com.tacz.legacy.client.render.weapon.WeaponVisualSampleDefinition
import com.tacz.legacy.client.render.weapon.WeaponVisualSampleRegistry
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import net.minecraft.client.Minecraft

public data class WeaponModelProbeState(
    val gunId: String,
    val displayDefinition: GunDisplayDefinition?,
    val visualSample: WeaponVisualSampleDefinition?
)

public fun interface WeaponModelPreviewRenderer {
    public fun render(textureAssetPath: String, sourceId: String?, context: RenderContext)
}

public class WeaponModelProbeFeature(
    private val stateProvider: () -> WeaponModelProbeState? = ::resolveStateFromRuntime,
    private val previewRenderer: WeaponModelPreviewRenderer = WeaponModelPreviewRenderer(::renderOverlayPreview)
) : RenderFeature {

    override val id: String = "builtin.weapon_model_probe"

    override fun install(registry: RenderFeatureRegistry) {
        registry.registerSubpass(
            FramePhase.UPDATE,
            SimpleSubpass("builtin.weapon_model_probe.update") { context ->
                val state = stateProvider.invoke()
                if (state == null) {
                    context.diagnostics[DIAG_ACTIVE] = false
                    context.diagnostics[DIAG_GATE_OPEN] = false
                    context.diagnostics[DIAG_BLOCKED_REASON] = "no_weapon_context"
                    incrementDiagnosticCounter(context.diagnostics, DIAG_BLOCKED_COUNT)
                    return@SimpleSubpass
                }

                val display = state.displayDefinition
                val visual = state.visualSample

                val modelReady = display?.modelParseSucceeded == true && !display.modelPath.isNullOrBlank()
                val animationReady = display?.animationParseSucceeded == true && !display.animationPath.isNullOrBlank()
                val visualReady = visual != null
                val gateOpen = modelReady && visualReady
                val previewTexturePath = resolvePreviewTexturePath(display, visual)

                context.diagnostics[DIAG_ACTIVE] = true
                context.diagnostics[DIAG_GUN_ID] = state.gunId
                context.diagnostics[DIAG_MODEL_READY] = modelReady
                context.diagnostics[DIAG_ANIMATION_READY] = animationReady
                context.diagnostics[DIAG_STATE_MACHINE_READY] = display?.stateMachineResolved == true
                context.diagnostics[DIAG_PLAYER_ANIMATOR_READY] = display?.playerAnimatorResolved == true
                context.diagnostics[DIAG_VISUAL_READY] = visualReady
                context.diagnostics[DIAG_GATE_OPEN] = gateOpen
                context.diagnostics[DIAG_PREVIEW_TEXTURE_PATH] = previewTexturePath ?: "none"
                context.diagnostics[DIAG_PREVIEW_SOURCE_ID] = display?.sourceId ?: "none"

                if (!gateOpen) {
                    context.diagnostics[DIAG_BLOCKED_REASON] = when {
                        !modelReady -> "model_not_ready"
                        !visualReady -> "visual_sample_missing"
                        else -> "unknown"
                    }
                    incrementDiagnosticCounter(context.diagnostics, DIAG_BLOCKED_COUNT)
                    return@SimpleSubpass
                }

                val resolvedDisplay = requireNotNull(display)
                val resolvedVisual = requireNotNull(visual)

                context.diagnostics[DIAG_BLOCKED_REASON] = "none"
                context.diagnostics[DIAG_LAST_MODEL_PATH] = resolvedDisplay.modelPath ?: resolvedVisual.firstPersonModelPath
                context.diagnostics[DIAG_LAST_ANIMATION_PATH] = resolvedDisplay.animationPath ?: resolvedVisual.idleAnimationPath

                context.commandBuffer.submit(
                    LambdaDrawCommand("builtin.weapon_model.submit.${state.gunId}") { renderContext ->
                        incrementDiagnosticCounter(renderContext.diagnostics, DIAG_EXECUTED_COUNT)
                        renderContext.diagnostics[DIAG_LAST_MODEL_PATH] =
                            resolvedDisplay.modelPath ?: resolvedVisual.firstPersonModelPath
                        renderContext.diagnostics[DIAG_LAST_ANIMATION_PATH] =
                            resolvedDisplay.animationPath ?: resolvedVisual.idleAnimationPath
                    }
                )

                incrementDiagnosticCounter(context.diagnostics, DIAG_QUEUED_COUNT)
            }
        )

        registry.registerSubpass(
            FramePhase.RENDER_OVERLAY,
            SimpleSubpass("builtin.weapon_model_probe.overlay_preview") { context ->
                if (!context.pipelineConfig.enableModelPreviewOverlay) {
                    context.diagnostics[DIAG_PREVIEW_SKIPPED_REASON] = "preview_disabled"
                    incrementDiagnosticCounter(context.diagnostics, DIAG_PREVIEW_SKIPPED_COUNT)
                    return@SimpleSubpass
                }

                if (context.diagnostics[DIAG_GATE_OPEN] != true) {
                    return@SimpleSubpass
                }

                val texturePath = context.diagnostics[DIAG_PREVIEW_TEXTURE_PATH] as? String
                val sourceId = (context.diagnostics[DIAG_PREVIEW_SOURCE_ID] as? String)
                    ?.takeUnless { it == "none" }
                if (texturePath.isNullOrBlank() || texturePath == "none") {
                    context.diagnostics[DIAG_PREVIEW_SKIPPED_REASON] = "no_preview_texture"
                    incrementDiagnosticCounter(context.diagnostics, DIAG_PREVIEW_SKIPPED_COUNT)
                    return@SimpleSubpass
                }

                previewRenderer.render(texturePath, sourceId, context)
                incrementDiagnosticCounter(context.diagnostics, DIAG_PREVIEW_RENDERED_COUNT)
            }
        )
    }

    private fun resolvePreviewTexturePath(
        display: GunDisplayDefinition?,
        visual: WeaponVisualSampleDefinition?
    ): String? {
        return display?.hudTexturePath
            ?: visual?.hudTexturePath
            ?: display?.slotTexturePath
            ?: display?.modelTexturePath
    }

    private companion object {
        private const val DIAG_ACTIVE: String = "weapon.model_probe.active"
        private const val DIAG_GUN_ID: String = "weapon.model_probe.gun_id"
        private const val DIAG_MODEL_READY: String = "weapon.model_probe.model_ready"
        private const val DIAG_ANIMATION_READY: String = "weapon.model_probe.animation_ready"
        private const val DIAG_STATE_MACHINE_READY: String = "weapon.model_probe.state_machine_ready"
        private const val DIAG_PLAYER_ANIMATOR_READY: String = "weapon.model_probe.player_animator_ready"
        private const val DIAG_VISUAL_READY: String = "weapon.model_probe.visual_ready"
        private const val DIAG_GATE_OPEN: String = "weapon.model_probe.gate_open"
        private const val DIAG_BLOCKED_REASON: String = "weapon.model_probe.blocked_reason"
        private const val DIAG_BLOCKED_COUNT: String = "weapon.model_probe.blocked"
        private const val DIAG_QUEUED_COUNT: String = "weapon.model_submit.queued"
        private const val DIAG_EXECUTED_COUNT: String = "weapon.model_submit.executed"
        private const val DIAG_LAST_MODEL_PATH: String = "weapon.model_submit.last_model_path"
        private const val DIAG_LAST_ANIMATION_PATH: String = "weapon.model_submit.last_animation_path"
        private const val DIAG_PREVIEW_TEXTURE_PATH: String = "weapon.model_preview.texture_path"
        private const val DIAG_PREVIEW_SOURCE_ID: String = "weapon.model_preview.source_id"
        private const val DIAG_PREVIEW_BOUND_RESOURCE: String = "weapon.model_preview.bound_resource"
        private const val DIAG_PREVIEW_RENDERED_COUNT: String = "weapon.model_preview.rendered"
        private const val DIAG_PREVIEW_SKIPPED_COUNT: String = "weapon.model_preview.skipped"
        private const val DIAG_PREVIEW_SKIPPED_REASON: String = "weapon.model_preview.skipped_reason"

        private fun resolveStateFromRuntime(): WeaponModelProbeState? {
            val minecraft = runCatching { Minecraft.getMinecraft() }.getOrNull() ?: return null
            val player = minecraft.player ?: return null
            if (minecraft.world == null) {
                return null
            }

            val gunId = player.heldItemMainhand
                .takeUnless { it.isEmpty }
                ?.item
                ?.registryName
                ?.toString()
                ?.substringAfter(':')
                ?.trim()
                ?.lowercase()
                ?.ifBlank { null }
                ?: return null

            val displayDefinition = GunDisplayRuntime.registry().snapshot().findDefinition(gunId)
            val visualSample = WeaponVisualSampleRegistry.resolve(gunId, displayDefinition)
            return WeaponModelProbeState(
                gunId = gunId,
                displayDefinition = displayDefinition,
                visualSample = visualSample
            )
        }

        private fun incrementDiagnosticCounter(diagnostics: MutableMap<String, Any>, key: String) {
            val count = (diagnostics[key] as? Int) ?: 0
            diagnostics[key] = count + 1
        }

        private fun renderOverlayPreview(textureAssetPath: String, sourceId: String?, context: RenderContext) {
            val minecraft = runCatching { Minecraft.getMinecraft() }.getOrNull()
            if (minecraft == null) {
                context.diagnostics[DIAG_PREVIEW_SKIPPED_REASON] = "minecraft_unavailable"
                incrementDiagnosticCounter(context.diagnostics, DIAG_PREVIEW_SKIPPED_COUNT)
                return
            }
            if (minecraft.world == null) {
                context.diagnostics[DIAG_PREVIEW_SKIPPED_REASON] = "world_unavailable"
                incrementDiagnosticCounter(context.diagnostics, DIAG_PREVIEW_SKIPPED_COUNT)
                return
            }

            val resourceLocation = TaczTextureResourceResolver.resolveForBind(
                rawPath = textureAssetPath,
                sourceId = sourceId,
                fallback = null,
                minecraft = minecraft
            )
            if (resourceLocation == null) {
                context.diagnostics[DIAG_PREVIEW_SKIPPED_REASON] = "texture_unavailable"
                incrementDiagnosticCounter(context.diagnostics, DIAG_PREVIEW_SKIPPED_COUNT)
                return
            }

            context.diagnostics[DIAG_PREVIEW_BOUND_RESOURCE] = resourceLocation.toString()

            val resolution = ScaledResolution(minecraft)
            val width = 52
            val height = 20
            val x = resolution.scaledWidth - width - 8
            val y = resolution.scaledHeight - 68

            minecraft.textureManager.bindTexture(resourceLocation)

            GlStateManager.pushMatrix()
            GlStateManager.enableBlend()
            GlStateManager.disableDepth()
            GlStateManager.color(1f, 1f, 1f, 0.85f)

            Gui.drawModalRectWithCustomSizedTexture(
                x,
                y,
                0f,
                0f,
                width,
                height,
                width.toFloat(),
                height.toFloat()
            )

            GlStateManager.enableDepth()
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()

            context.diagnostics[DIAG_PREVIEW_SKIPPED_REASON] = "none"
        }
    }

}
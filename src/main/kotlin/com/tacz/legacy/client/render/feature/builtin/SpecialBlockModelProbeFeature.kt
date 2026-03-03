package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.block.LegacySpecialBlockModelDescriptor
import com.tacz.legacy.client.render.block.LegacySpecialBlockModelRegistry
import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.draw.LambdaDrawCommand
import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.SimpleSubpass
import net.minecraft.client.Minecraft
import net.minecraft.util.math.RayTraceResult

public fun interface SpecialBlockModelRenderer {
    public fun render(descriptor: LegacySpecialBlockModelDescriptor, context: RenderContext)
}

public class SpecialBlockModelProbeFeature(
    private val targetBlockPathResolver: () -> String? = ::resolveTargetedBlockRegistryPathFromMinecraft,
    private val descriptorResolver: (String) -> LegacySpecialBlockModelDescriptor? = { blockRegistryPath ->
        LegacySpecialBlockModelRegistry.find(blockRegistryPath)
    },
    private val renderer: SpecialBlockModelRenderer = SpecialBlockModelRenderer { descriptor, context ->
        context.diagnostics[DIAG_LAST_RENDER_MODE] = "stub_noop"
        context.diagnostics[DIAG_LAST_MODEL] = descriptor.modelResourcePath
        context.diagnostics[DIAG_LAST_RENDER_TAG] = descriptor.debugTag
    }
) : RenderFeature {

    override val id: String = "builtin.special_block_model_probe"

    override fun install(registry: RenderFeatureRegistry) {
        registry.registerSubpass(
            FramePhase.RENDER_OPAQUE,
            SimpleSubpass("builtin.special_block_model_probe.opaque") { context ->
                if (!context.pipelineConfig.enableSpecialBlockModelProbe) {
                    context.diagnostics[DIAG_ACTIVE] = false
                    context.diagnostics[DIAG_SKIPPED_REASON] = "feature_disabled"
                    incrementDiagnosticCounter(context.diagnostics, DIAG_SKIPPED_COUNT)
                    return@SimpleSubpass
                }

                val blockRegistryPath = targetBlockPathResolver.invoke()
                if (blockRegistryPath == null) {
                    context.diagnostics[DIAG_ACTIVE] = false
                    context.diagnostics[DIAG_SKIPPED_REASON] = "no_target_block"
                    incrementDiagnosticCounter(context.diagnostics, DIAG_SKIPPED_COUNT)
                    return@SimpleSubpass
                }

                val descriptor = descriptorResolver.invoke(blockRegistryPath)
                if (descriptor == null) {
                    context.diagnostics[DIAG_ACTIVE] = false
                    context.diagnostics[DIAG_SKIPPED_REASON] = "unsupported_block"
                    context.diagnostics[DIAG_BLOCK_PATH] = blockRegistryPath
                    incrementDiagnosticCounter(context.diagnostics, DIAG_SKIPPED_COUNT)
                    return@SimpleSubpass
                }

                context.diagnostics[DIAG_ACTIVE] = true
                context.diagnostics[DIAG_SKIPPED_REASON] = "none"
                context.diagnostics[DIAG_BLOCK_PATH] = descriptor.blockRegistryPath
                context.diagnostics[DIAG_LAST_MODEL] = descriptor.modelResourcePath
                context.diagnostics[DIAG_LAST_RENDER_TAG] = descriptor.debugTag
                context.diagnostics[DIAG_TRANSLUCENT_HINT] = descriptor.translucent

                context.commandBuffer.submit(
                    LambdaDrawCommand("builtin.special_block_model.submit.${descriptor.blockRegistryPath}") { renderContext ->
                        renderer.render(descriptor, renderContext)
                        incrementDiagnosticCounter(renderContext.diagnostics, DIAG_RENDERED_COUNT)
                    }
                )
                incrementDiagnosticCounter(context.diagnostics, DIAG_QUEUED_COUNT)
            }
        )
    }

    private companion object {
        private const val DIAG_ACTIVE: String = "block.model_probe.active"
        private const val DIAG_BLOCK_PATH: String = "block.model_probe.block_path"
        private const val DIAG_TRANSLUCENT_HINT: String = "block.model_probe.translucent_hint"
        private const val DIAG_SKIPPED_REASON: String = "block.model_probe.skipped_reason"
        private const val DIAG_QUEUED_COUNT: String = "block.model_probe.queued"
        private const val DIAG_RENDERED_COUNT: String = "block.model_probe.rendered"
        private const val DIAG_SKIPPED_COUNT: String = "block.model_probe.skipped"
        private const val DIAG_LAST_MODEL: String = "block.model_probe.last_model"
        private const val DIAG_LAST_RENDER_MODE: String = "block.model_probe.last_render_mode"
        private const val DIAG_LAST_RENDER_TAG: String = "block.model_probe.last_render_tag"

        private fun incrementDiagnosticCounter(diagnostics: MutableMap<String, Any>, key: String) {
            val count = (diagnostics[key] as? Int) ?: 0
            diagnostics[key] = count + 1
        }
    }

}

private fun resolveTargetedBlockRegistryPathFromMinecraft(): String? {
    val minecraft = runCatching { Minecraft.getMinecraft() }.getOrNull() ?: return null
    val world = minecraft.world ?: return null
    val hitResult = minecraft.objectMouseOver ?: return null
    if (hitResult.typeOfHit != RayTraceResult.Type.BLOCK) {
        return null
    }

    val blockPos = hitResult.blockPos ?: return null
    val state = world.getBlockState(blockPos)
    return state.block.registryName?.path?.trim()?.lowercase()?.ifBlank { null }
}

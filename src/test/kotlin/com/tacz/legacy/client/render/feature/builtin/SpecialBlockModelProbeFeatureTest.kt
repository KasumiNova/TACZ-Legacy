package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.block.LegacySpecialBlockModelDescriptor
import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.core.RenderPipelineCoordinator
import com.tacz.legacy.client.render.frame.FramePhase
import org.junit.Assert.assertEquals
import org.junit.Test

public class SpecialBlockModelProbeFeatureTest {

    @Test
    public fun `special block probe should skip when feature is disabled`() {
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(
            SpecialBlockModelProbeFeature(
                targetBlockPathResolver = { "weapon_workbench" },
                descriptorResolver = { sampleDescriptor() }
            )
        )

        val context = sampleContext(config = RenderPipelineConfig(enableSpecialBlockModelProbe = false))

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)

        assertEquals(false, context.diagnostics["block.model_probe.active"])
        assertEquals("feature_disabled", context.diagnostics["block.model_probe.skipped_reason"])
        assertEquals(1, context.diagnostics["block.model_probe.skipped"])
        assertEquals(0, (context.diagnostics["block.model_probe.queued"] as? Int) ?: 0)
        assertEquals(0, (context.diagnostics["block.model_probe.rendered"] as? Int) ?: 0)
    }

    @Test
    public fun `special block probe should report unsupported block when no descriptor`() {
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(
            SpecialBlockModelProbeFeature(
                targetBlockPathResolver = { "minecraft:stone" },
                descriptorResolver = { null }
            )
        )

        val context = sampleContext(config = RenderPipelineConfig(enableSpecialBlockModelProbe = true))

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)

        assertEquals(false, context.diagnostics["block.model_probe.active"])
        assertEquals("unsupported_block", context.diagnostics["block.model_probe.skipped_reason"])
        assertEquals("minecraft:stone", context.diagnostics["block.model_probe.block_path"])
        assertEquals(1, context.diagnostics["block.model_probe.skipped"])
        assertEquals(0, (context.diagnostics["block.model_probe.queued"] as? Int) ?: 0)
    }

    @Test
    public fun `special block probe should queue and render command when descriptor resolved`() {
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(
            SpecialBlockModelProbeFeature(
                targetBlockPathResolver = { "weapon_workbench" },
                descriptorResolver = { sampleDescriptor() }
            )
        )
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)

        val context = sampleContext(config = RenderPipelineConfig(enableSpecialBlockModelProbe = true))

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)

        assertEquals(true, context.diagnostics["block.model_probe.active"])
        assertEquals("none", context.diagnostics["block.model_probe.skipped_reason"])
        assertEquals("weapon_workbench", context.diagnostics["block.model_probe.block_path"])
        assertEquals("tacz:block/gun_smith_table", context.diagnostics["block.model_probe.last_model"])
        assertEquals("builtin.weapon_workbench", context.diagnostics["block.model_probe.last_render_tag"])
        assertEquals("stub_noop", context.diagnostics["block.model_probe.last_render_mode"])
        assertEquals(1, context.diagnostics["block.model_probe.queued"])
        assertEquals(1, context.diagnostics["block.model_probe.rendered"])
    }

    private fun sampleContext(config: RenderPipelineConfig): RenderContext =
        RenderContext(
            frameId = 1L,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = config
        )

    private fun sampleDescriptor(): LegacySpecialBlockModelDescriptor =
        LegacySpecialBlockModelDescriptor(
            blockRegistryPath = "weapon_workbench",
            modelResourcePath = "tacz:block/gun_smith_table",
            debugTag = "builtin.weapon_workbench",
            translucent = false
        )
}

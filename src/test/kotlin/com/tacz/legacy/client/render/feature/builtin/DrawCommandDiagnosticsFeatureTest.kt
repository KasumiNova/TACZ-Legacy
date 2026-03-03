package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.core.RenderPipelineCoordinator
import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.SimpleSubpass
import org.junit.Assert.assertEquals
import org.junit.Test

public class DrawCommandDiagnosticsFeatureTest {

    @Test
    public fun `draw command diagnostics should submit and consume commands across phases`() {
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)

        val context = RenderContext(
            frameId = 1,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = RenderPipelineConfig()
        )

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)

        assertEquals(1, context.diagnostics["draw.command.submitted"])
        assertEquals(1, context.diagnostics["draw.command.executed"])
        assertEquals(1, context.diagnostics["draw.command.consumed"])
        assertEquals(0, context.commandBuffer.size())
    }

    @Test
    public fun `draw command diagnostics should record failed commands when fallback enabled`() {
        val coordinator = RenderPipelineCoordinator()
        coordinator.registerFeature(object : RenderFeature {
            override val id: String = "test.fail_draw_command"

            override fun install(registry: RenderFeatureRegistry) {
                registry.registerSubpass(
                    FramePhase.UPDATE,
                    SimpleSubpass("test.fail_draw_command.submit") { context ->
                        context.commandBuffer.submit(
                            com.tacz.legacy.client.render.draw.LambdaDrawCommand("test.fail") { _ ->
                                error("expected failure")
                            }
                        )
                    }
                )
            }
        })
        coordinator.registerFeature(DrawCommandDiagnosticsFeature)

        val context = RenderContext(
            frameId = 2,
            partialTicks = 0.0f,
            finishTimeNano = 0L,
            pipelineConfig = RenderPipelineConfig(enableVanillaFallback = true)
        )

        coordinator.runPhase(FramePhase.PREPARE, context)
        coordinator.runPhase(FramePhase.PRE_UPDATE, context)
        coordinator.runPhase(FramePhase.UPDATE, context)
        coordinator.runPhase(FramePhase.RENDER_OPAQUE, context)

        assertEquals(1, context.diagnostics["draw.command.failed"])
        assertEquals(listOf("test.fail"), context.diagnostics["draw.command.failed.ids"])
        assertEquals(2, context.diagnostics["draw.command.consumed"])
        assertEquals(1, context.diagnostics["draw.command.executed"])
    }

}

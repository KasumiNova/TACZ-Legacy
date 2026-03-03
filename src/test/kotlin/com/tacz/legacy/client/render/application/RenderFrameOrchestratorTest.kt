package com.tacz.legacy.client.render.application

import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.execution.FrameEventPhasePlan
import com.tacz.legacy.client.render.execution.FrameEventTrigger
import com.tacz.legacy.client.render.frame.FramePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class RenderFrameOrchestratorTest {

    @Test
    public fun `tick start should abort when world unavailable`() {
        val runtime = FakeRenderPipelineRuntimePort()
        val orchestrator = RenderFrameOrchestrator(runtime)

        orchestrator.onRenderTickStart(0.0f, 1L, worldAvailable = false)

        assertEquals("world_unavailable", runtime.lastAbortReason)
        assertEquals(0, runtime.beginFrameCalls)
    }

    @Test
    public fun `overlay trigger should run overlay phases and complete frame`() {
        val runtime = FakeRenderPipelineRuntimePort(active = true)
        val orchestrator = RenderFrameOrchestrator(runtime)

        orchestrator.onOverlayPreAll()

        assertTrue(runtime.runPhasesCalls.isNotEmpty())
        assertEquals(
            listOf(FramePhase.RENDER_OVERLAY),
            runtime.runPhasesCalls.last()
        )
        assertEquals(1, runtime.completeFrameCalls)
    }

    @Test
    public fun `tick end should compensate when enabled`() {
        val runtime = FakeRenderPipelineRuntimePort(
            active = true,
            config = RenderPipelineConfig(enablePhaseCompensation = true)
        )
        val orchestrator = RenderFrameOrchestrator(runtime)

        orchestrator.onRenderTickEnd()

        assertEquals("render_tick_end_compensation", runtime.lastCompensateReason)
    }

    @Test
    public fun `tick end should abort when compensation disabled`() {
        val runtime = FakeRenderPipelineRuntimePort(
            active = true,
            config = RenderPipelineConfig(enablePhaseCompensation = false)
        )
        val orchestrator = RenderFrameOrchestrator(runtime)

        orchestrator.onRenderTickEnd()

        assertEquals("render_tick_end_incomplete", runtime.lastAbortReason)
    }

    private class FakeRenderPipelineRuntimePort(
        active: Boolean = false,
        config: RenderPipelineConfig = RenderPipelineConfig(),
        phasePlan: FrameEventPhasePlan = FrameEventPhasePlan.defaultPlan()
    ) : RenderPipelineRuntimePort {

        private var hasActive: Boolean = active
        private var currentConfig: RenderPipelineConfig = config
        private val currentPhasePlan: FrameEventPhasePlan = phasePlan

        var beginFrameCalls: Int = 0
            private set
        var completeFrameCalls: Int = 0
            private set
        var runPhasesCalls: MutableList<List<FramePhase>> = mutableListOf()
            private set
        var lastAbortReason: String? = null
            private set
        var lastCompensateReason: String? = null
            private set

        private var frameCounter: Long = 0L

        override fun hasActiveFrame(): Boolean = hasActive

        override fun beginFrame(partialTicks: Float, finishTimeNano: Long): RenderContext {
            beginFrameCalls += 1
            hasActive = true
            frameCounter += 1
            return RenderContext(
                frameId = frameCounter,
                partialTicks = partialTicks,
                finishTimeNano = finishTimeNano,
                pipelineConfig = currentConfig
            )
        }

        override fun runPhases(phases: List<FramePhase>): RenderContext? {
            runPhasesCalls += phases
            if (!hasActive) {
                return null
            }
            return RenderContext(
                frameId = frameCounter,
                partialTicks = 0.0f,
                finishTimeNano = 0L,
                pipelineConfig = currentConfig
            )
        }

        override fun completeFrame(): RenderContext? {
            completeFrameCalls += 1
            if (!hasActive) {
                return null
            }
            hasActive = false
            return RenderContext(
                frameId = frameCounter,
                partialTicks = 0.0f,
                finishTimeNano = 0L,
                pipelineConfig = currentConfig
            )
        }

        override fun abortActiveFrame(reason: String) {
            lastAbortReason = reason
            hasActive = false
        }

        override fun completeRemainingPhases(reason: String): RenderContext? {
            lastCompensateReason = reason
            if (!hasActive) {
                return null
            }
            hasActive = false
            return RenderContext(
                frameId = frameCounter,
                partialTicks = 0.0f,
                finishTimeNano = 0L,
                pipelineConfig = currentConfig
            )
        }

        override fun currentConfig(): RenderPipelineConfig = currentConfig

        override fun phasePlan(): FrameEventPhasePlan = currentPhasePlan

    }

}

package com.tacz.legacy.client.render.application

import com.tacz.legacy.client.render.execution.FrameEventTrigger

public class RenderFrameOrchestrator(
    private val runtime: RenderPipelineRuntimePort
) {

    public fun onRenderTickStart(partialTicks: Float, finishTimeNano: Long, worldAvailable: Boolean) {
        if (!worldAvailable) {
            runtime.abortActiveFrame("world_unavailable")
            return
        }

        val context = runtime.beginFrame(partialTicks, finishTimeNano) ?: return
        if (!context.pipelineConfig.enableNewPipeline) {
            context.diagnostics["pipeline.skipped"] = true
            runtime.completeFrame()
            return
        }

        runPlannedPhases(FrameEventTrigger.RENDER_TICK_START)
    }

    public fun onRenderWorldLast(worldAvailable: Boolean) {
        if (!worldAvailable || !runtime.hasActiveFrame()) {
            return
        }

        runPlannedPhases(FrameEventTrigger.RENDER_WORLD_LAST)
    }

    public fun onOverlayPreAll() {
        if (!runtime.hasActiveFrame()) {
            return
        }

        runPlannedPhases(FrameEventTrigger.OVERLAY_PRE_ALL)
        runtime.completeFrame()
    }

    public fun onRenderTickEnd() {
        if (!runtime.hasActiveFrame()) {
            return
        }

        val config = runtime.currentConfig()
        if (config.enablePhaseCompensation) {
            runtime.completeRemainingPhases("render_tick_end_compensation")
        } else {
            runtime.abortActiveFrame("render_tick_end_incomplete")
        }
    }

    private fun runPlannedPhases(trigger: FrameEventTrigger) {
        val phases = runtime.phasePlan().phasesFor(trigger)
        runtime.runPhases(phases)
    }

}

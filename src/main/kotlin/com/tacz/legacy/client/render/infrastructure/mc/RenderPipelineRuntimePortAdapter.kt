package com.tacz.legacy.client.render.infrastructure.mc

import com.tacz.legacy.client.render.RenderPipelineRuntime
import com.tacz.legacy.client.render.application.RenderPipelineRuntimePort
import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.execution.FrameEventPhasePlan
import com.tacz.legacy.client.render.frame.FramePhase

public object RenderPipelineRuntimePortAdapter : RenderPipelineRuntimePort {

    override fun hasActiveFrame(): Boolean = RenderPipelineRuntime.hasActiveFrame()

    override fun beginFrame(partialTicks: Float, finishTimeNano: Long): RenderContext? =
        RenderPipelineRuntime.beginFrame(partialTicks, finishTimeNano)

    override fun runPhases(phases: List<FramePhase>): RenderContext? =
        RenderPipelineRuntime.runPhases(phases)

    override fun completeFrame(): RenderContext? = RenderPipelineRuntime.completeFrame()

    override fun abortActiveFrame(reason: String) {
        RenderPipelineRuntime.abortActiveFrame(reason)
    }

    override fun completeRemainingPhases(reason: String): RenderContext? =
        RenderPipelineRuntime.completeRemainingPhases(reason)

    override fun currentConfig(): RenderPipelineConfig = RenderPipelineRuntime.currentConfig()

    override fun phasePlan(): FrameEventPhasePlan = RenderPipelineRuntime.phasePlan()

}

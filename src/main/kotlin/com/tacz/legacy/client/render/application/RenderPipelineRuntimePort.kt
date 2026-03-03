package com.tacz.legacy.client.render.application

import com.tacz.legacy.client.render.core.RenderContext
import com.tacz.legacy.client.render.core.RenderPipelineConfig
import com.tacz.legacy.client.render.execution.FrameEventPhasePlan
import com.tacz.legacy.client.render.frame.FramePhase

public interface RenderPipelineRuntimePort {

    public fun hasActiveFrame(): Boolean

    public fun beginFrame(partialTicks: Float, finishTimeNano: Long): RenderContext?

    public fun runPhases(phases: List<FramePhase>): RenderContext?

    public fun completeFrame(): RenderContext?

    public fun abortActiveFrame(reason: String)

    public fun completeRemainingPhases(reason: String): RenderContext?

    public fun currentConfig(): RenderPipelineConfig

    public fun phasePlan(): FrameEventPhasePlan

}

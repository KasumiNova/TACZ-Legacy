package com.tacz.legacy.client.render.core

import com.tacz.legacy.client.render.frame.FramePhase

public object RenderPipelineBootstrap {

    public fun createDefaultConfig(): RenderPipelineConfig = RenderPipelineConfig()

    public fun createDefaultPhaseOrder(): List<FramePhase> = FramePhase.defaultOrder()

}

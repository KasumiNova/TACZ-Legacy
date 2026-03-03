package com.tacz.legacy.client.render.core

import com.tacz.legacy.client.render.draw.DrawCommandBuffer

public data class RenderContext(
    val frameId: Long,
    val partialTicks: Float,
    val finishTimeNano: Long,
    val pipelineConfig: RenderPipelineConfig,
    val diagnostics: MutableMap<String, Any> = mutableMapOf(),
    val commandBuffer: DrawCommandBuffer = DrawCommandBuffer()
)

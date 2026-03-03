package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.SimpleSubpass

public object NoopFeature : RenderFeature {

    override val id: String = "builtin.noop"

    override fun install(registry: RenderFeatureRegistry) {
        registry.registerSubpass(
            FramePhase.UPDATE,
            SimpleSubpass("builtin.noop.update") { context ->
                val count = (context.diagnostics["feature.noop.hits"] as? Int) ?: 0
                context.diagnostics["feature.noop.hits"] = count + 1
            }
        )
    }

}

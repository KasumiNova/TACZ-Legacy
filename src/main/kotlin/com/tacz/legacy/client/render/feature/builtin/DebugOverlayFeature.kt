package com.tacz.legacy.client.render.feature.builtin

import com.tacz.legacy.client.render.feature.RenderFeature
import com.tacz.legacy.client.render.feature.RenderFeatureRegistry
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.SimpleSubpass

public object DebugOverlayFeature : RenderFeature {

    override val id: String = "builtin.debug_overlay"

    override fun install(registry: RenderFeatureRegistry) {
        registry.registerSubpass(
            FramePhase.RENDER_OVERLAY,
            SimpleSubpass("builtin.debug_overlay.overlay") { context ->
                context.diagnostics["feature.debug_overlay.active"] = true
            }
        )
    }

}

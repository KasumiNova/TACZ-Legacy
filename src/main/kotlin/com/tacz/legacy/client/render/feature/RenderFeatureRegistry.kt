package com.tacz.legacy.client.render.feature

import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.pass.RenderPass
import com.tacz.legacy.client.render.pass.Subpass

public class RenderFeatureRegistry internal constructor(
    private val passMap: MutableMap<FramePhase, RenderPass>
) {

    public fun registerSubpass(phase: FramePhase, subpass: Subpass) {
        val pass = requireNotNull(passMap[phase]) {
            "No RenderPass found for phase: $phase"
        }
        pass.addSubpass(subpass)
    }

}

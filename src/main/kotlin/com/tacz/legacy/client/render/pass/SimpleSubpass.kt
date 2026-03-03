package com.tacz.legacy.client.render.pass

import com.tacz.legacy.client.render.core.RenderContext

public class SimpleSubpass(
    override val id: String,
    private val block: (RenderContext) -> Unit
) : Subpass {

    override fun render(context: RenderContext) {
        block(context)
    }

}

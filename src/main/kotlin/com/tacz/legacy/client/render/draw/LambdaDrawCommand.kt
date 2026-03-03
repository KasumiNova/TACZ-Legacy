package com.tacz.legacy.client.render.draw

import com.tacz.legacy.client.render.core.RenderContext

public class LambdaDrawCommand(
    override val id: String,
    private val block: (RenderContext) -> Unit
) : DrawCommand {

    override fun execute(context: RenderContext) {
        block(context)
    }

}

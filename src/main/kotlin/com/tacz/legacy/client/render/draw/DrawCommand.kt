package com.tacz.legacy.client.render.draw

import com.tacz.legacy.client.render.core.RenderContext

public interface DrawCommand {

    public val id: String

    public fun execute(context: RenderContext)

}

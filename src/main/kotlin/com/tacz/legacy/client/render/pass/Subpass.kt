package com.tacz.legacy.client.render.pass

import com.tacz.legacy.client.render.core.RenderContext

public interface Subpass {

    public val id: String

    public fun render(context: RenderContext)

}

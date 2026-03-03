package com.tacz.legacy.client.render.pass

import com.tacz.legacy.client.render.core.RenderContext

public class RenderPass(
    public val id: String
) {

    private val subpasses: MutableList<Subpass> = mutableListOf()

    public fun addSubpass(subpass: Subpass) {
        subpasses += subpass
    }

    public fun removeSubpass(subpassId: String): Boolean = subpasses.removeIf { it.id == subpassId }

    public fun clearSubpasses() {
        subpasses.clear()
    }

    public fun listSubpassIds(): List<String> = subpasses.map { it.id }

    public fun render(context: RenderContext) {
        subpasses.forEach { subpass ->
            subpass.render(context)
        }
    }

}

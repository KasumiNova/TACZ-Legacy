package com.tacz.legacy.client.render.feature

public interface RenderFeature {

    public val id: String

    public fun install(registry: RenderFeatureRegistry)

}

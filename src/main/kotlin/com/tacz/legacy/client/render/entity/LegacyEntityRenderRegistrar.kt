package com.tacz.legacy.client.render.entity

import com.tacz.legacy.common.infrastructure.mc.entity.LegacyBulletEntity
import net.minecraftforge.fml.client.registry.IRenderFactory
import net.minecraftforge.fml.client.registry.RenderingRegistry

public object LegacyEntityRenderRegistrar {

    @Volatile
    private var registered: Boolean = false

    @Synchronized
    public fun registerAll() {
        if (registered) {
            return
        }

        RenderingRegistry.registerEntityRenderingHandler(
            LegacyBulletEntity::class.java,
            IRenderFactory { renderManager ->
                LegacyBulletRenderer(renderManager)
            }
        )

        registered = true
    }
}

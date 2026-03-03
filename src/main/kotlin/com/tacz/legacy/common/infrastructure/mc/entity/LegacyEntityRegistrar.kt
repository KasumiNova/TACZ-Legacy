package com.tacz.legacy.common.infrastructure.mc.entity

import com.tacz.legacy.TACZLegacy
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.EntityRegistry

public object LegacyEntityRegistrar {

    @Volatile
    private var registered: Boolean = false

    @Synchronized
    public fun registerEntities() {
        if (registered) {
            return
        }

        EntityRegistry.registerModEntity(
            ResourceLocation(TACZLegacy.MOD_ID, LEGACY_BULLET_ENTITY_NAME),
            LegacyBulletEntity::class.java,
            LEGACY_BULLET_ENTITY_NAME,
            LEGACY_BULLET_ENTITY_NUMERIC_ID,
            TACZLegacy,
            TRACKING_RANGE,
            UPDATE_FREQUENCY,
            SENDS_VELOCITY_UPDATES
        )

        registered = true
    }

    private const val LEGACY_BULLET_ENTITY_NAME: String = "legacy_bullet"
    private const val LEGACY_BULLET_ENTITY_NUMERIC_ID: Int = 1
    private const val TRACKING_RANGE: Int = 96
    private const val UPDATE_FREQUENCY: Int = 1
    private const val SENDS_VELOCITY_UPDATES: Boolean = true
}
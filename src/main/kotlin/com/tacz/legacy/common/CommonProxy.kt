package com.tacz.legacy.common

import com.tacz.legacy.common.block.entity.LegacyBlockEntities
import com.tacz.legacy.common.event.LegacyMissingMappingHandler
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.registry.BlockRegisterer
import com.tacz.legacy.common.registry.EntityRegisterer
import com.tacz.legacy.common.registry.ItemRegisterer
import com.tacz.legacy.common.registry.SoundRegisterer
import net.minecraftforge.common.MinecraftForge

internal open class CommonProxy {
    init {
        MinecraftForge.EVENT_BUS.register(BlockRegisterer)
        MinecraftForge.EVENT_BUS.register(ItemRegisterer)
        MinecraftForge.EVENT_BUS.register(EntityRegisterer)
        MinecraftForge.EVENT_BUS.register(SoundRegisterer)
        MinecraftForge.EVENT_BUS.register(LegacyMissingMappingHandler)
        MinecraftForge.EVENT_BUS.register(ShooterTickHandler)
    }

    internal open fun preInit(): Unit {
        LegacyBlockEntities.registerAll()
        TACZNetworkHandler.init()
    }

    internal open fun init(): Unit = Unit

    internal open fun postInit(): Unit = Unit
}

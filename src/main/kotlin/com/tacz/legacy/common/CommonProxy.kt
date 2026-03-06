package com.tacz.legacy.common

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.block.entity.LegacyBlockEntities
import com.tacz.legacy.common.event.LegacyMissingMappingHandler
import com.tacz.legacy.common.gui.LegacyGuiHandler
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.registry.BlockRegisterer
import com.tacz.legacy.common.registry.EntityRegisterer
import com.tacz.legacy.common.registry.ItemRegisterer
import com.tacz.legacy.common.registry.SoundRegisterer
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.network.NetworkRegistry

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
        NetworkRegistry.INSTANCE.registerGuiHandler(TACZLegacy, LegacyGuiHandler)
    }

    internal open fun init(): Unit = Unit

    internal open fun postInit(): Unit = Unit

    internal open fun createClientGuiElement(id: Int, player: EntityPlayer, world: World, pos: BlockPos): Any? = null
}

package com.tacz.legacy.common.gui

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.inventory.GunSmithTableContainer
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.network.IGuiHandler

internal object LegacyGuiHandler : IGuiHandler {
    override fun getServerGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
        return when (id) {
            LegacyGuiIds.GUN_SMITH_TABLE -> GunSmithTableContainer(player.inventory, world, BlockPos(x, y, z))
            else -> null
        }
    }

    override fun getClientGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
        return TACZLegacy.proxy.createClientGuiElement(id, player, world, BlockPos(x, y, z))
    }
}

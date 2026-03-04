package com.tacz.legacy.common.infrastructure.mc.registry.block

import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.workbench.WeaponWorkbenchSessionRegistry
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.World

public class LegacyWeaponWorkbenchBlock(
    registryPath: String,
    material: Material,
    creativeTab: CreativeTabs = CreativeTabs.DECORATIONS,
    hardness: Float = 3.5f,
    resistance: Float = 12.0f
) : LegacySimpleBlock(
    registryPath = registryPath,
    material = material,
    creativeTab = creativeTab,
    hardness = hardness,
    resistance = resistance
) {

    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float
    ): Boolean {
        if (hand != EnumHand.MAIN_HAND) {
            return false
        }

        if (worldIn.isRemote) {
            return true
        }

        val serverPlayer = playerIn as? EntityPlayerMP ?: return true
        val gunId = WeaponWorkbenchSessionRegistry.resolveHeldLegacyGunId(serverPlayer)
        if (gunId.isNullOrBlank()) {
            WeaponWorkbenchSessionRegistry.end(serverPlayer)
            serverPlayer.sendStatusMessage(TextComponentString("[TACZ] 请先手持枪械后再使用改枪台"), true)
            return true
        }

        WeaponWorkbenchSessionRegistry.begin(serverPlayer, pos, gunId)
        LegacyNetworkHandler.sendWeaponWorkbenchOpenToClient(
            player = serverPlayer,
            gunId = gunId,
            blockPos = pos
        )
        return true
    }
}

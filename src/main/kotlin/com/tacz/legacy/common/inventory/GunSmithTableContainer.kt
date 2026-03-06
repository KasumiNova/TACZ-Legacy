package com.tacz.legacy.common.inventory

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.common.block.entity.GunSmithTableTileEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

internal class GunSmithTableContainer(
    private val playerInventory: InventoryPlayer,
    private val world: World,
    val blockPos: BlockPos,
) : Container() {
    val blockId: ResourceLocation = (world.getTileEntity(blockPos) as? GunSmithTableTileEntity)?.blockId ?: DefaultAssets.DEFAULT_BLOCK_ID

    init {
        addHiddenPlayerInventory(playerInventory)
    }

    override fun canInteractWith(playerIn: EntityPlayer): Boolean {
        val tile = world.getTileEntity(blockPos) as? GunSmithTableTileEntity ?: return false
        return tile.blockId == blockId && playerIn.getDistanceSq(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5) <= 64.0
    }

    override fun transferStackInSlot(playerIn: EntityPlayer, index: Int): ItemStack = ItemStack.EMPTY

    private fun addHiddenPlayerInventory(inventory: InventoryPlayer) {
        for (row in 0 until 3) {
            for (column in 0 until 9) {
                addSlotToContainer(Slot(inventory, column + row * 9 + 9, HIDDEN_SLOT_X, HIDDEN_SLOT_Y))
            }
        }
        for (column in 0 until 9) {
            addSlotToContainer(Slot(inventory, column, HIDDEN_SLOT_X, HIDDEN_SLOT_Y))
        }
    }

    private companion object {
        private const val HIDDEN_SLOT_X: Int = -2000
        private const val HIDDEN_SLOT_Y: Int = -2000
    }
}

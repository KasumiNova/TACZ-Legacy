package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.common.infrastructure.mc.registry.LegacyCreativeTabs
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

public class LegacyAmmoBoxItem(
    registryPath: String,
    ammoId: String,
    roundsPerUse: Int,
    public val capacity: Int,
    sourceId: String? = null,
    iconTextureAssetPath: String? = null,
    creativeTab: CreativeTabs = LegacyCreativeTabs.OTHER
) : LegacyAmmoItem(
    registryPath = registryPath,
    ammoId = ammoId,
    roundsPerItem = roundsPerUse,
    sourceId = sourceId,
    iconTextureAssetPath = iconTextureAssetPath,
    creativeTab = creativeTab
) {

    init {
        maxStackSize = 1
    }

    override fun shouldConsumeStackOnUse(): Boolean = false

    override fun onItemRightClick(
        worldIn: World,
        playerIn: net.minecraft.entity.player.EntityPlayer,
        handIn: EnumHand
    ): net.minecraft.util.ActionResult<ItemStack> {
        val stack = playerIn.getHeldItem(handIn)
        val remainingBefore = readRemainingRounds(stack)
        if (remainingBefore <= 0) {
            return net.minecraft.util.ActionResult(EnumActionResult.FAIL, stack)
        }

        val result = super.onItemRightClick(worldIn, playerIn, handIn)
        if (result.type != EnumActionResult.SUCCESS) {
            return result
        }

        if (!playerIn.capabilities.isCreativeMode) {
            val consumed = roundsPerItem.coerceAtLeast(1)
            val remainingAfter = (remainingBefore - consumed).coerceAtLeast(0)
            writeRemainingRounds(stack, remainingAfter)
            if (remainingAfter <= 0) {
                stack.shrink(1)
            }
        }

        return result
    }

    override fun onCreated(stack: ItemStack, worldIn: World, playerIn: net.minecraft.entity.player.EntityPlayer) {
        super.onCreated(stack, worldIn, playerIn)
        if (!stack.hasTagCompound()) {
            stack.tagCompound = NBTTagCompound()
        }
        if (!stack.tagCompound!!.hasKey(TAG_REMAINING_ROUNDS)) {
            stack.tagCompound!!.setInteger(TAG_REMAINING_ROUNDS, capacity.coerceAtLeast(1))
        }
    }

    override fun getSubItems(tab: CreativeTabs, items: net.minecraft.util.NonNullList<ItemStack>) {
        if (!isInCreativeTab(tab)) {
            return
        }
        val full = ItemStack(this)
        full.tagCompound = NBTTagCompound().apply {
            setInteger(TAG_REMAINING_ROUNDS, capacity.coerceAtLeast(1))
        }
        items.add(full)
    }

    private fun readRemainingRounds(stack: ItemStack): Int {
        val tag = stack.tagCompound ?: return capacity.coerceAtLeast(1)
        if (!tag.hasKey(TAG_REMAINING_ROUNDS)) {
            return capacity.coerceAtLeast(1)
        }
        return tag.getInteger(TAG_REMAINING_ROUNDS).coerceAtLeast(0)
    }

    private fun writeRemainingRounds(stack: ItemStack, remainingRounds: Int) {
        if (!stack.hasTagCompound()) {
            stack.tagCompound = NBTTagCompound()
        }
        stack.tagCompound!!.setInteger(TAG_REMAINING_ROUNDS, remainingRounds.coerceAtLeast(0))
    }

    private companion object {
        private const val TAG_REMAINING_ROUNDS: String = "remaining_rounds"
    }
}

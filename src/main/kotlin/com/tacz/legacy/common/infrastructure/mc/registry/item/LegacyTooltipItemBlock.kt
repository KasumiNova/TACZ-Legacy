package com.tacz.legacy.common.infrastructure.mc.registry.item

import net.minecraft.block.Block
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

public class LegacyTooltipItemBlock(
    block: Block
) : ItemBlock(block) {

    @SideOnly(Side.CLIENT)
    override fun addInformation(
        stack: ItemStack,
        worldIn: World?,
        tooltip: MutableList<String>,
        flagIn: ITooltipFlag
    ) {
        super.addInformation(stack, worldIn, tooltip, flagIn)

        val blockId = stack.item.registryName?.toString() ?: return
        val indexedEntry = LegacyIndexedTooltipSupport.resolveBlockEntry(blockId)
        LegacyIndexedTooltipSupport.appendIndexedLines(
            tooltip = tooltip,
            stackDisplayName = stack.displayName,
            entry = indexedEntry
        )
    }
}

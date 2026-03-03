package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.common.infrastructure.mc.registry.LegacyCreativeTabs
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

public class LegacyGunItem(
    registryPath: String,
    creativeTab: CreativeTabs = LegacyCreativeTabs.GUN_ALL,
    private val extraCreativeTabs: Set<CreativeTabs> = emptySet()
) : LegacySimpleItem(
    registryPath = registryPath,
    creativeTab = creativeTab,
    maxStackSize = 1
) {

    override fun isInCreativeTab(targetTab: CreativeTabs): Boolean {
        if (super.isInCreativeTab(targetTab)) {
            return true
        }
        return extraCreativeTabs.any { it === targetTab }
    }

    public fun isVisibleInCreativeTab(targetTab: CreativeTabs): Boolean = isInCreativeTab(targetTab)

    override fun shouldCauseReequipAnimation(oldStack: ItemStack, newStack: ItemStack, slotChanged: Boolean): Boolean {
        if (slotChanged) {
            return true
        }

        // 枪械在客户端可能随状态同步产生 NBT 变化，
        // 这里保持同物品不触发“拿起物品”重装备动画。
        return oldStack.item !== newStack.item
    }

    override fun shouldCauseBlockBreakReset(oldStack: ItemStack, newStack: ItemStack): Boolean {
        // 与重装备动画策略一致：同枪械切换状态不重置挖掘进度。
        return oldStack.item !== newStack.item
    }

    override fun onEntitySwing(entityLiving: EntityLivingBase, stack: ItemStack): Boolean {
        // 拦截原版 swingArm，避免枪械射击触发手臂挥砍动画。
        return true
    }

    @SideOnly(Side.CLIENT)
    override fun addInformation(
        stack: ItemStack,
        worldIn: World?,
        tooltip: MutableList<String>,
        flagIn: ITooltipFlag
    ) {
        super.addInformation(stack, worldIn, tooltip, flagIn)
        tooltip += "§7左键：开火"
        tooltip += "§7潜行 + 右键：换弹"
    }

}

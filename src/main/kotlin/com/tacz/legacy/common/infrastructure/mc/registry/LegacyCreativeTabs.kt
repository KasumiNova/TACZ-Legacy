package com.tacz.legacy.common.infrastructure.mc.registry

import net.minecraft.creativetab.CreativeTabs
import net.minecraft.init.Items
import net.minecraft.item.ItemStack

public object LegacyCreativeTabs {

    public val GUN_ALL: CreativeTabs = tab("tacz.guns.all") { ItemStack(Items.IRON_SWORD) }
    public val GUN_PISTOL: CreativeTabs = tab("tacz.guns.pistol") { ItemStack(Items.FLINT_AND_STEEL) }
    public val GUN_SNIPER: CreativeTabs = tab("tacz.guns.sniper") { ItemStack(Items.BOW) }
    public val GUN_RIFLE: CreativeTabs = tab("tacz.guns.rifle") { ItemStack(Items.DIAMOND_SWORD) }
    public val GUN_SHOTGUN: CreativeTabs = tab("tacz.guns.shotgun") { ItemStack(Items.IRON_AXE) }
    public val GUN_SMG: CreativeTabs = tab("tacz.guns.smg") { ItemStack(Items.GOLDEN_SWORD) }
    public val GUN_RPG: CreativeTabs = tab("tacz.guns.rpg") { ItemStack(Items.BLAZE_ROD) }
    public val GUN_MG: CreativeTabs = tab("tacz.guns.mg") { ItemStack(Items.IRON_PICKAXE) }
    public val OTHER: CreativeTabs = tab("tacz.other") { ItemStack(Items.REDSTONE) }

    public fun tabFor(type: LegacyGunTabType): CreativeTabs =
        when (type) {
            LegacyGunTabType.PISTOL -> GUN_PISTOL
            LegacyGunTabType.SNIPER -> GUN_SNIPER
            LegacyGunTabType.RIFLE -> GUN_RIFLE
            LegacyGunTabType.SHOTGUN -> GUN_SHOTGUN
            LegacyGunTabType.SMG -> GUN_SMG
            LegacyGunTabType.RPG -> GUN_RPG
            LegacyGunTabType.MG -> GUN_MG
            LegacyGunTabType.UNKNOWN -> GUN_ALL
        }

    private fun tab(label: String, iconSupplier: () -> ItemStack): CreativeTabs {
        return object : CreativeTabs(label) {
            override fun createIcon(): ItemStack = runCatching {
                val icon = iconSupplier()
                if (icon.isEmpty) ItemStack.EMPTY else icon.copy()
            }.getOrDefault(ItemStack.EMPTY)
        }
    }
}

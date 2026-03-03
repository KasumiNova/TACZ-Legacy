package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.TACZLegacy
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.Item

public open class LegacySimpleItem(
    registryPath: String,
    creativeTab: CreativeTabs = CreativeTabs.MISC,
    maxStackSize: Int = 64
) : Item() {

    init {
        setRegistryName(TACZLegacy.MOD_ID, registryPath)
        translationKey = "${TACZLegacy.MOD_ID}.$registryPath"
        setCreativeTab(creativeTab)
        this.maxStackSize = maxStackSize
    }

}

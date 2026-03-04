package com.tacz.legacy.common.infrastructure.mc.registry.block

import com.tacz.legacy.TACZLegacy
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.creativetab.CreativeTabs

public open class LegacySimpleBlock(
    private val registryPath: String,
    material: Material,
    creativeTab: CreativeTabs = CreativeTabs.BUILDING_BLOCKS,
    hardness: Float = 2.0f,
    resistance: Float = 10.0f
) : Block(material) {

    init {
        setRegistryName(TACZLegacy.MOD_ID, registryPath)
        translationKey = "${TACZLegacy.MOD_ID}.$registryPath"
        setCreativeTab(creativeTab)
        setHardness(hardness)
        setResistance(resistance)
    }

}

package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.common.infrastructure.mc.registry.block.LegacySimpleBlock
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.creativetab.CreativeTabs

public object LegacyBlocks {

    public val weaponWorkbench: Block = LegacySimpleBlock(
        registryPath = LegacyContentIds.WEAPON_WORKBENCH,
        material = Material.IRON,
        creativeTab = CreativeTabs.DECORATIONS,
        hardness = 3.5f,
        resistance = 12.0f
    )

    public val steelTarget: Block = LegacySimpleBlock(
        registryPath = LegacyContentIds.STEEL_TARGET,
        material = Material.IRON,
        creativeTab = CreativeTabs.DECORATIONS,
        hardness = 4.5f,
        resistance = 18.0f
    )

    public fun all(): List<Block> = listOf(
        weaponWorkbench,
        steelTarget
    )

}

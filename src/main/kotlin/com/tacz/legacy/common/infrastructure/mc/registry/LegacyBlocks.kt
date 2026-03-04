package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.common.infrastructure.mc.registry.block.LegacySimpleBlock
import com.tacz.legacy.common.infrastructure.mc.registry.block.LegacyWeaponWorkbenchBlock
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.creativetab.CreativeTabs

public object LegacyBlocks {

    public val weaponWorkbench: Block = createWorkbenchBlock(LegacyContentIds.WEAPON_WORKBENCH)
    public val gunSmithTable: Block = createWorkbenchBlock(LegacyContentIds.GUN_SMITH_TABLE)
    public val workbenchA: Block = createWorkbenchBlock(LegacyContentIds.WORKBENCH_A)
    public val workbenchB: Block = createWorkbenchBlock(LegacyContentIds.WORKBENCH_B)
    public val workbenchC: Block = createWorkbenchBlock(LegacyContentIds.WORKBENCH_C)

    public val steelTarget: Block = createDecorativeBlock(
        registryPath = LegacyContentIds.STEEL_TARGET,
        material = Material.IRON,
        hardness = 4.5f,
        resistance = 18.0f
    )
    public val target: Block = createDecorativeBlock(
        registryPath = LegacyContentIds.TARGET,
        material = Material.IRON,
        hardness = 4.0f,
        resistance = 16.0f
    )
    public val statue: Block = createDecorativeBlock(
        registryPath = LegacyContentIds.STATUE,
        material = Material.ROCK,
        hardness = 3.0f,
        resistance = 15.0f
    )

    private val workbenchBlockSet: Set<Block> by lazy {
        setOf(weaponWorkbench, gunSmithTable, workbenchA, workbenchB, workbenchC)
    }

    public fun isWeaponWorkbenchBlock(block: Block): Boolean = block in workbenchBlockSet

    public fun all(): List<Block> = listOf(
        weaponWorkbench,
        gunSmithTable,
        workbenchA,
        workbenchB,
        workbenchC,
        steelTarget,
        target,
        statue
    )

    private fun createWorkbenchBlock(registryPath: String): Block = LegacyWeaponWorkbenchBlock(
        registryPath = registryPath,
        material = Material.IRON,
        creativeTab = CreativeTabs.DECORATIONS,
        hardness = 3.5f,
        resistance = 12.0f
    )

    private fun createDecorativeBlock(
        registryPath: String,
        material: Material,
        hardness: Float,
        resistance: Float
    ): Block = LegacySimpleBlock(
        registryPath = registryPath,
        material = material,
        creativeTab = CreativeTabs.DECORATIONS,
        hardness = hardness,
        resistance = resistance
    )

}

package com.tacz.legacy.common.infrastructure.mc.registry

public object LegacyContentIds {

    public const val AK47: String = "ak47"
    public const val WEAPON_DEBUG_CORE: String = "weapon_debug_core"

    public const val WEAPON_WORKBENCH: String = "weapon_workbench"
    public const val GUN_SMITH_TABLE: String = "gun_smith_table"
    public const val WORKBENCH_A: String = "workbench_a"
    public const val WORKBENCH_B: String = "workbench_b"
    public const val WORKBENCH_C: String = "workbench_c"

    public const val STEEL_TARGET: String = "steel_target"
    public const val TARGET: String = "target"
    public const val STATUE: String = "statue"

    public fun itemIds(): List<String> = listOf(AK47, WEAPON_DEBUG_CORE)

    public fun blockIds(): List<String> = listOf(
        WEAPON_WORKBENCH,
        GUN_SMITH_TABLE,
        WORKBENCH_A,
        WORKBENCH_B,
        WORKBENCH_C,
        STEEL_TARGET,
        TARGET,
        STATUE
    )

    public fun workbenchBlockIds(): Set<String> = setOf(
        WEAPON_WORKBENCH,
        GUN_SMITH_TABLE,
        WORKBENCH_A,
        WORKBENCH_B,
        WORKBENCH_C
    )

}

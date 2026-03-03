package com.tacz.legacy.common.infrastructure.mc.registry

public object LegacyContentIds {

    public const val AK47: String = "ak47"
    public const val WEAPON_DEBUG_CORE: String = "weapon_debug_core"

    public const val WEAPON_WORKBENCH: String = "weapon_workbench"
    public const val STEEL_TARGET: String = "steel_target"

    public fun itemIds(): List<String> = listOf(AK47, WEAPON_DEBUG_CORE)

    public fun blockIds(): List<String> = listOf(WEAPON_WORKBENCH, STEEL_TARGET)

}

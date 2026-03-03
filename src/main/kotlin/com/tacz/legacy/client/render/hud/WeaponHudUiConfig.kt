package com.tacz.legacy.client.render.hud

public enum class WeaponCrosshairType(
    public val texturePath: String
) {
    DOT_1("textures/crosshair/normal/dot_1.png"),
    CROSS_1("textures/crosshair/normal/cross_1.png"),
    CIRCLE_1("textures/crosshair/normal/circle_1.png")
}

public data class WeaponHudUiConfig(
    val gunHudEnabled: Boolean = true,
    val replaceVanillaCrosshair: Boolean = true,
    val crosshairType: WeaponCrosshairType = WeaponCrosshairType.DOT_1,
    val killAmountEnabled: Boolean = true,
    val interactHintEnabled: Boolean = true
)

public object WeaponHudUiRuntime {

    @Volatile
    private var config: WeaponHudUiConfig = WeaponHudUiConfig()

    public fun currentConfig(): WeaponHudUiConfig = config

    @Synchronized
    public fun replace(newConfig: WeaponHudUiConfig): WeaponHudUiConfig {
        config = newConfig
        return config
    }

}

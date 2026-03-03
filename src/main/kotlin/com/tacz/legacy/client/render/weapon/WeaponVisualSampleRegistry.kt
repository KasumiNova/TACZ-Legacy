package com.tacz.legacy.client.render.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition

public data class WeaponVisualSampleDefinition(
    val gunId: String,
    val firstPersonModelPath: String,
    val thirdPersonModelPath: String,
    val idleAnimationPath: String,
    val fireAnimationPath: String,
    val reloadAnimationPath: String,
    val hudTexturePath: String,
    val hudEmptyTexturePath: String? = null
)

public object WeaponVisualSampleRegistry {

    private val definitionsByGunId: Map<String, WeaponVisualSampleDefinition> = linkedMapOf(
        "ak47" to WeaponVisualSampleDefinition(
            gunId = "ak47",
            firstPersonModelPath = "assets/tacz/models/item/ak47_fp.json",
            thirdPersonModelPath = "assets/tacz/models/item/ak47_tp.json",
            idleAnimationPath = "assets/tacz/animations/weapon/ak47/idle.json",
            fireAnimationPath = "assets/tacz/animations/weapon/ak47/fire.json",
            reloadAnimationPath = "assets/tacz/animations/weapon/ak47/reload.json",
            hudTexturePath = "assets/tacz/textures/hud/weapon/ak47.png",
            hudEmptyTexturePath = "assets/tacz/textures/hud/weapon/ak47_empty.png"
        )
    )

    public fun resolve(
        gunId: String?,
        displayDefinition: GunDisplayDefinition? = null
    ): WeaponVisualSampleDefinition? {
        val normalized = (gunId ?: displayDefinition?.gunId)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (normalized.isEmpty()) {
            return null
        }

        val fallback = definitionsByGunId[normalized]
        if (displayDefinition == null) {
            return fallback
        }

        val modelPath = displayDefinition.modelPath ?: fallback?.firstPersonModelPath
        val thirdPersonModelPath = displayDefinition.lodModelPath
            ?: displayDefinition.modelPath
            ?: fallback?.thirdPersonModelPath
        val animationPath = displayDefinition.animationPath

        if (modelPath == null || thirdPersonModelPath == null) {
            return fallback
        }

        return WeaponVisualSampleDefinition(
            gunId = normalized,
            firstPersonModelPath = modelPath,
            thirdPersonModelPath = thirdPersonModelPath,
            idleAnimationPath = animationPath ?: fallback?.idleAnimationPath ?: DEFAULT_IDLE_ANIMATION,
            fireAnimationPath = animationPath ?: fallback?.fireAnimationPath ?: DEFAULT_FIRE_ANIMATION,
            reloadAnimationPath = animationPath ?: fallback?.reloadAnimationPath ?: DEFAULT_RELOAD_ANIMATION,
            hudTexturePath = displayDefinition.hudTexturePath
                ?: fallback?.hudTexturePath
                ?: DEFAULT_HUD_TEXTURE,
            hudEmptyTexturePath = displayDefinition.hudEmptyTexturePath ?: fallback?.hudEmptyTexturePath
        )
    }

    public fun registeredGunIds(): Set<String> = definitionsByGunId.keys

    private const val DEFAULT_IDLE_ANIMATION: String = "assets/tacz/animations/default.animation.json"
    private const val DEFAULT_FIRE_ANIMATION: String = "assets/tacz/animations/default.animation.json"
    private const val DEFAULT_RELOAD_ANIMATION: String = "assets/tacz/animations/default.animation.json"
    private const val DEFAULT_HUD_TEXTURE: String = "assets/tacz/textures/hud/heat_bar.png"

}

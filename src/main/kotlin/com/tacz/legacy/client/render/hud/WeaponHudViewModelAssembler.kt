package com.tacz.legacy.client.render.hud

import com.tacz.legacy.client.render.weapon.WeaponVisualSampleDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.weapon.WeaponDefinition
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeSnapshot
import java.util.Locale

public data class WeaponHudViewModel(
    val gunId: String,
    val state: WeaponState?,
    val fireMode: WeaponFireMode?,
    val ammoInMagazine: Int,
    val ammoReserve: Int,
    val currentAmmoText: String,
    val reserveAmmoText: String,
    val currentAmmoColor: Int,
    val reserveAmmoColor: Int,
    val showCrosshair: Boolean,
    val crosshairType: WeaponCrosshairType,
    val reloadProgress: Float,
    val cooldownProgress: Float,
    val totalShotsFired: Int,
    val animationClip: String?,
    val animationProgress: Float,
    val hudTexturePath: String?,
    val hudEmptyTexturePath: String?,
    val hudTextureSourceId: String?
)

public class WeaponHudViewModelAssembler {

    public fun assemble(
        fallbackGunId: String?,
        definition: WeaponDefinition?,
        sessionDebugSnapshot: WeaponSessionDebugSnapshot?,
        displayDefinition: GunDisplayDefinition? = null,
        animationRuntimeSnapshot: WeaponAnimationRuntimeSnapshot? = null,
        visualSample: WeaponVisualSampleDefinition?,
        uiConfig: WeaponHudUiConfig = WeaponHudUiRuntime.currentConfig()
    ): WeaponHudViewModel? {
        val gunId = sessionDebugSnapshot?.gunId
            ?: definition?.gunId
            ?: fallbackGunId?.trim()?.lowercase()?.ifBlank { null }
            ?: return null

        val maxMagazine = definition?.spec?.magazineSize?.coerceAtLeast(1) ?: 1
        val state = sessionDebugSnapshot?.snapshot?.state
        val fireMode = definition?.spec?.fireMode
        val snapshot = sessionDebugSnapshot?.snapshot

        val ammoInMagazine = (snapshot?.ammoInMagazine ?: maxMagazine)
            .coerceIn(0, MAX_AMMO_DISPLAY)
        val ammoReserve = (snapshot?.ammoReserve ?: 0)
            .coerceIn(0, MAX_AMMO_DISPLAY)

        val reloadProgress = if (snapshot != null && state == WeaponState.RELOADING && definition != null) {
            val reloadTicks = definition.spec.reloadTicks.coerceAtLeast(1)
            (snapshot.reloadTicksRemaining.toFloat() / reloadTicks.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val cooldownProgress = if (snapshot != null && definition != null && snapshot.cooldownTicksRemaining > 0) {
            val interval = definition.spec.shotIntervalTicks().coerceAtLeast(1)
            (snapshot.cooldownTicksRemaining.toFloat() / interval.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val lowAmmo = ammoInMagazine < (maxMagazine * LOW_AMMO_RATIO) && ammoInMagazine < LOW_AMMO_ABSOLUTE_THRESHOLD
        val ammoColor = if (lowAmmo) LOW_AMMO_COLOR else DEFAULT_AMMO_COLOR

        val hudTexturePath = displayDefinition?.hudTexturePath ?: visualSample?.hudTexturePath
        val hudEmptyTexturePath = displayDefinition?.hudEmptyTexturePath ?: visualSample?.hudEmptyTexturePath
        val hudTextureSourceId = displayDefinition?.sourceId
        val canShowCrosshair = displayDefinition?.showCrosshair ?: true

        return WeaponHudViewModel(
            gunId = gunId,
            state = state,
            fireMode = fireMode,
            ammoInMagazine = ammoInMagazine,
            ammoReserve = ammoReserve,
            currentAmmoText = formatCurrentAmmo(ammoInMagazine),
            reserveAmmoText = formatReserveAmmo(ammoReserve),
            currentAmmoColor = ammoColor,
            reserveAmmoColor = RESERVE_AMMO_COLOR,
            showCrosshair = canShowCrosshair && state != WeaponState.RELOADING,
            crosshairType = uiConfig.crosshairType,
            reloadProgress = reloadProgress,
            cooldownProgress = cooldownProgress,
            totalShotsFired = snapshot?.totalShotsFired ?: 0,
            animationClip = animationRuntimeSnapshot?.clip?.name,
            animationProgress = animationRuntimeSnapshot?.progress?.coerceIn(0f, 1f) ?: 0f,
            hudTexturePath = hudTexturePath,
            hudEmptyTexturePath = hudEmptyTexturePath,
            hudTextureSourceId = hudTextureSourceId
        )
    }

    private fun formatCurrentAmmo(value: Int): String = String.format(Locale.ROOT, "%03d", value)

    private fun formatReserveAmmo(value: Int): String = String.format(Locale.ROOT, "%04d", value)

    private companion object {
        private const val MAX_AMMO_DISPLAY: Int = 9999
        private const val LOW_AMMO_RATIO: Double = 0.25
        private const val LOW_AMMO_ABSOLUTE_THRESHOLD: Int = 10

        private const val DEFAULT_AMMO_COLOR: Int = 0xFFFFFF
        private const val LOW_AMMO_COLOR: Int = 0xFF5555
        private const val RESERVE_AMMO_COLOR: Int = 0xAAAAAA
    }

}

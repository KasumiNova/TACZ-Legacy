package com.tacz.legacy.client.sound

import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.sound.SoundManager
import net.minecraft.entity.Entity
import kotlin.math.roundToInt

internal object TACZClientGunSoundCoordinator {
    private var drySoundTrack: Boolean = true
    private var tmpSoundInstance: TACZClientSoundHandle? = null

    internal fun stopPlayGunSound() {
        tmpSoundInstance?.stop()
        tmpSoundInstance = null
    }

    internal fun stopPlayGunSound(display: GunDisplayInstance?, soundName: String) {
        val currentInstance = tmpSoundInstance ?: return
        val targetSound = display?.getSound(soundName) ?: return
        if (currentInstance.soundId == targetSound) {
            currentInstance.stop()
            tmpSoundInstance = null
        }
    }

    internal fun resetDryFireSound() {
        drySoundTrack = true
    }

    internal fun playShootSound(entity: Entity, display: GunDisplayInstance?, gunData: GunCombatData) {
        playFireSound(
            entity = entity,
            soundId = display?.getSound(SoundManager.SHOOT_SOUND),
            volume = 0.8f,
            pitch = randomizedPitch(entity),
            distance = (LegacyConfigManager.common.defaultGunFireSoundDistance * gunData.fireSoundMultiplier)
                .roundToInt()
                .coerceAtLeast(0),
        )
    }

    internal fun playSilenceSound(entity: Entity, display: GunDisplayInstance?, gunData: GunCombatData) {
        playFireSound(
            entity = entity,
            soundId = display?.getSound(SoundManager.SILENCE_SOUND),
            volume = 0.6f,
            pitch = randomizedPitch(entity),
            distance = (LegacyConfigManager.common.defaultGunSilenceSoundDistance * gunData.silenceSoundMultiplier)
                .roundToInt()
                .coerceAtLeast(0),
        )
    }

    internal fun playDryFireSound(entity: Entity, display: GunDisplayInstance?) {
        if (!drySoundTrack) {
            return
        }
        GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.DRY_FIRE_SOUND),
            1.0f,
            randomizedPitch(entity),
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
        drySoundTrack = false
    }

    internal fun playReloadSound(entity: Entity, display: GunDisplayInstance?, noAmmo: Boolean) {
        tmpSoundInstance = GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(if (noAmmo) SoundManager.RELOAD_EMPTY_SOUND else SoundManager.RELOAD_TACTICAL_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playInspectSound(entity: Entity, display: GunDisplayInstance?, noAmmo: Boolean) {
        tmpSoundInstance = GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(if (noAmmo) SoundManager.INSPECT_EMPTY_SOUND else SoundManager.INSPECT_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playBoltSound(entity: Entity, display: GunDisplayInstance?) {
        tmpSoundInstance = GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.BOLT_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playDrawSound(entity: Entity, display: GunDisplayInstance?) {
        tmpSoundInstance = GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.DRAW_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playPutAwaySound(entity: Entity, display: GunDisplayInstance?) {
        tmpSoundInstance = GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.PUT_AWAY_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playFireSelectSound(entity: Entity, display: GunDisplayInstance?) {
        GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.FIRE_SELECT),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playHeadHitSound(entity: Entity, display: GunDisplayInstance?) {
        GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.HEAD_HIT_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playFleshHitSound(entity: Entity, display: GunDisplayInstance?) {
        GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.FLESH_HIT_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    internal fun playKillSound(entity: Entity, display: GunDisplayInstance?) {
        GunSoundPlayManager.playClientSound(
            entity,
            display?.getSound(SoundManager.KILL_SOUND),
            1.0f,
            1.0f,
            LegacyConfigManager.common.defaultGunOtherSoundDistance,
        )
    }

    private fun playFireSound(entity: Entity, soundId: net.minecraft.util.ResourceLocation?, volume: Float, pitch: Float, distance: Int) {
        GunSoundPlayManager.playClientSound(entity, soundId, volume, pitch, distance)
    }

    private fun randomizedPitch(entity: Entity): Float = 0.9f + entity.world.rand.nextFloat() * 0.125f
}
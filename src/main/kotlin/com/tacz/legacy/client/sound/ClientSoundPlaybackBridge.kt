package com.tacz.legacy.client.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.audio.ISound
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory

public object ClientSoundPlaybackBridge {

    @JvmStatic
    public fun tryPlayRawSound(
        soundId: String,
        x: Float,
        y: Float,
        z: Float,
        volume: Float,
        pitch: Float,
        category: String
    ): Boolean {
        val normalizedSoundId = soundId.trim().lowercase().ifBlank { return false }

        if (TaczSoundEngine.isSoundLoaded(normalizedSoundId)) {
            TaczSoundEngine.playSound(
                soundKey = normalizedSoundId,
                x = x,
                y = y,
                z = z,
                volume = volume.coerceAtLeast(0f),
                pitch = pitch.coerceIn(0.5f, 2f)
            )
            return true
        }

        val location = parseResourceLocation(normalizedSoundId) ?: return false
        val minecraft = runCatching { Minecraft.getMinecraft() }.getOrNull() ?: return false
        val soundHandler = runCatching { minecraft.soundHandler }.getOrNull() ?: return false

        val resolvedCategory = SoundCategory.values().firstOrNull {
            it.name.equals(category, ignoreCase = true)
        } ?: SoundCategory.PLAYERS

        return runCatching {
            soundHandler.playSound(
                PositionedSoundRecord(
                    location,
                    resolvedCategory,
                    volume.coerceAtLeast(0f),
                    pitch.coerceIn(0.5f, 2f),
                    false,
                    0,
                    ISound.AttenuationType.LINEAR,
                    x,
                    y,
                    z
                )
            )
            true
        }.getOrDefault(false)
    }

    private fun parseResourceLocation(soundId: String): ResourceLocation? {
        val normalized = soundId.trim().lowercase().ifBlank { return null }
        return runCatching {
            if (normalized.contains(':')) {
                ResourceLocation(normalized)
            } else {
                ResourceLocation(DEFAULT_NAMESPACE, normalized)
            }
        }.getOrNull()
    }

    private const val DEFAULT_NAMESPACE: String = "tacz"
}

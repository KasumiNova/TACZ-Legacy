package com.tacz.legacy.client.sound;

import com.tacz.legacy.TACZLegacy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;

/**
 * Client-side sound playback for gun animation sounds.
 * Port of upstream TACZ SoundPlayManager, adapted for 1.12.2.
 */
@SideOnly(Side.CLIENT)
public class GunSoundPlayManager {
    private static final Marker MARKER = MarkerManager.getMarker("GunSound");
    private static final SoundPlaybackBackend MINECRAFT_BACKEND = GunSoundPlayManager::playWithMinecraft;
    private static SoundPlaybackBackend playbackBackend = MINECRAFT_BACKEND;

    @Nullable
    public static GunSoundInstance playClientSound(@Nullable Entity entity, @Nullable ResourceLocation soundName, float volume, float pitch, int distance) {
        if (soundName == null) {
            return null;
        }
        return playbackBackend.play(entity, soundName, volume, pitch, distance);
    }

    /**
     * Public test seam for interval/sound-channel tests.
     */
    public static void setPlaybackBackendForTesting(SoundPlaybackBackend backend) {
        playbackBackend = backend == null ? MINECRAFT_BACKEND : backend;
    }

    public static void resetPlaybackBackendForTesting() {
        playbackBackend = MINECRAFT_BACKEND;
    }

    public static float applyClientDistanceMix(@Nullable Entity entity, float volume, int distance) {
        if (entity == null || distance <= 0) {
            return volume;
        }
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            EntityPlayerSP player = minecraft == null ? null : minecraft.player;
            if (player == null) {
                return volume;
            }
            float mixVolume = volume * (1.0F - Math.min(1.0F, (float) Math.sqrt(player.getDistanceSq(entity.posX, entity.posY, entity.posZ)) / distance));
            return mixVolume * mixVolume;
        } catch (NoClassDefFoundError ignored) {
            return volume;
        }
    }

    @Nullable
    private static GunSoundInstance playWithMinecraft(@Nullable Entity entity, ResourceLocation soundName, float volume, float pitch, int distance) {
        if (entity == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getSoundHandler() == null) {
            return null;
        }
        GunSoundInstance instance = new GunSoundInstance(entity, distance, soundName, volume, pitch);
        TACZLegacy.logger.debug(MARKER, "Playing gun pack sound {} (vol={}, pitch={}, dist={})", soundName, volume, pitch, distance);
        minecraft.getSoundHandler().playSound(instance);
        return instance;
    }

    @FunctionalInterface
    public interface SoundPlaybackBackend {
        @Nullable
        GunSoundInstance play(@Nullable Entity entity, ResourceLocation soundName, float volume, float pitch, int distance);
    }
}

package com.tacz.legacy.api.client.animation;

import com.tacz.legacy.client.sound.GunSoundPlayManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;

public class ObjectAnimationSoundChannel {
    public AnimationSoundChannelContent content;

    public ObjectAnimationSoundChannel() {
    }

    public ObjectAnimationSoundChannel(AnimationSoundChannelContent content) {
        this.content = content;
    }

    /**
     * 播放区间内的所有声音。时间区间左开右闭。
     * Port of upstream TACZ ObjectAnimationSoundChannel.playSound.
     */
    public void playSound(double fromTimeS, double toTimeS, Entity entity, int distance, float volume, float pitch) {
        if (content == null) {
            return;
        }
        if (fromTimeS == toTimeS) {
            return;
        }
        if (fromTimeS > toTimeS && fromTimeS <= getEndTimeS()) {
            playSound(0, toTimeS, entity, distance, volume, pitch);
            toTimeS = getEndTimeS();
        }
        int to = computeIndex(toTimeS, false);
        int from = computeIndex(fromTimeS, true);
        float mixVolume = GunSoundPlayManager.applyClientDistanceMix(entity, volume, distance);
        for (int i = from + 1; i <= to; i++) {
            ResourceLocation name = content.keyframeSoundName[i];
            GunSoundPlayManager.playClientSound(entity, name, mixVolume, pitch, distance);
        }
    }

    public double getEndTimeS() {
        return content.keyframeTimeS[content.keyframeTimeS.length - 1];
    }

    private int computeIndex(double timeS, boolean open) {
        int index = Arrays.binarySearch(content.keyframeTimeS, timeS);
        if (index >= 0) {
            return open ? index - 1 : index;
        }
        return -index - 2;
    }
}

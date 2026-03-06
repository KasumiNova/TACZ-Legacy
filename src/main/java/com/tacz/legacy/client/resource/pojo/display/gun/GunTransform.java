package com.tacz.legacy.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import com.tacz.legacy.client.resource.pojo.TransformScale;

/**
 * Transform data for a gun display. Port of upstream TACZ GunTransform.
 */
public class GunTransform {
    @SerializedName("scale")
    private TransformScale scale;

    public static GunTransform getDefault() {
        GunTransform gunTransform = new GunTransform();
        gunTransform.scale = TransformScale.getGunDefault();
        return gunTransform;
    }

    public TransformScale getScale() {
        return scale;
    }
}

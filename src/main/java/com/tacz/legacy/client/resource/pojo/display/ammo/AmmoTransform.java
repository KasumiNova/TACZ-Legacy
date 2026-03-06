package com.tacz.legacy.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import com.tacz.legacy.client.resource.pojo.TransformScale;

/**
 * Ammo transform POJO — nested inside AmmoDisplay.
 * Port of upstream TACZ AmmoTransform.
 */
public class AmmoTransform {
    @SerializedName("scale")
    private TransformScale scale;

    public static AmmoTransform getDefault() {
        AmmoTransform t = new AmmoTransform();
        t.scale = TransformScale.getAmmoDefault();
        return t;
    }

    public TransformScale getScale() {
        return scale;
    }
}

package com.tacz.legacy.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.ResourceLocation;

/**
 * LOD variant for a gun model. Port of upstream TACZ GunLod.
 */
public class GunLod {
    @SerializedName("model")
    private ResourceLocation modelLocation;
    @SerializedName("texture")
    protected ResourceLocation modelTexture;

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    public void setModelTexture(ResourceLocation modelTexture) {
        this.modelTexture = modelTexture;
    }
}

package com.tacz.legacy.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Ammo display POJO — deserialized from gun pack ammo display JSON.
 * Port of upstream TACZ AmmoDisplay.
 */
public class AmmoDisplay {
    @SerializedName("model")
    private ResourceLocation modelLocation;

    @SerializedName("texture")
    private ResourceLocation modelTexture;

    @Nullable
    @SerializedName("slot")
    private ResourceLocation slotTextureLocation;

    @Nullable
    @SerializedName("transform")
    private AmmoTransform transform;

    @SerializedName("tracer_color")
    private String tracerColor = "0xFFFFFF";

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    @Nullable
    public ResourceLocation getSlotTextureLocation() {
        return slotTextureLocation;
    }

    @Nullable
    public AmmoTransform getTransform() {
        return transform;
    }

    public String getTracerColor() {
        return tracerColor;
    }

    /**
     * Post-deserialization texture path conversion.
     * Converts short texture names to full resource paths.
     */
    public void init() {
        if (modelTexture != null) {
            modelTexture = expandTexturePath(modelTexture);
        }
        if (slotTextureLocation != null) {
            slotTextureLocation = expandTexturePath(slotTextureLocation);
        }
    }

    private static ResourceLocation expandTexturePath(ResourceLocation shortId) {
        return new ResourceLocation(shortId.getNamespace(), "textures/" + shortId.getPath() + ".png");
    }
}

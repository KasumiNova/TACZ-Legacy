package com.tacz.legacy.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.ResourceLocation;

public class ShellDisplay {
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
}
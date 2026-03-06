package com.tacz.legacy.client.resource.pojo.display.attachment;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Attachment LOD POJO — nested inside AttachmentDisplay.
 * Port of upstream TACZ AttachmentLod.
 */
public class AttachmentLod {
    @SerializedName("model")
    private ResourceLocation modelLocation;

    @SerializedName("texture")
    @Nullable
    ResourceLocation modelTexture;

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    @Nullable
    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    public void setModelTexture(ResourceLocation modelTexture) {
        this.modelTexture = modelTexture;
    }
}

package com.tacz.legacy.client.resource.pojo.display.attachment;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Attachment display POJO — deserialized from gun pack attachment display JSON.
 * Port of upstream TACZ AttachmentDisplay.
 */
public class AttachmentDisplay {
    @SerializedName("slot")
    private ResourceLocation slotTextureLocation;

    @SerializedName("model")
    private ResourceLocation model;

    @SerializedName("texture")
    private ResourceLocation texture;

    @Nullable
    @SerializedName("lod")
    private AttachmentLod attachmentLod;

    @Nullable
    @SerializedName("adapter")
    private String adapterNodeName;

    @SerializedName("show_muzzle")
    private boolean showMuzzle = false;

    @Nullable
    @SerializedName("zoom")
    private float[] zoom;

    @SerializedName("scope")
    private boolean isScope = false;

    @SerializedName("sight")
    private boolean isSight = false;

    @SerializedName("fov")
    private float fov = 70;

    @Nullable
    @SerializedName("sounds")
    private Map<String, ResourceLocation> sounds;

    public ResourceLocation getSlotTextureLocation() {
        return slotTextureLocation;
    }

    public ResourceLocation getModel() {
        return model;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    @Nullable
    public AttachmentLod getAttachmentLod() {
        return attachmentLod;
    }

    @Nullable
    public String getAdapterNodeName() {
        return adapterNodeName;
    }

    public boolean isShowMuzzle() {
        return showMuzzle;
    }

    @Nullable
    public float[] getZoom() {
        return zoom;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public float getFov() {
        return fov;
    }

    @Nullable
    public Map<String, ResourceLocation> getSounds() {
        return sounds;
    }

    /**
     * Post-deserialization texture path conversion.
     */
    public void init() {
        if (slotTextureLocation != null) {
            slotTextureLocation = expandTexturePath(slotTextureLocation);
        }
        if (texture != null) {
            texture = expandTexturePath(texture);
        }
        if (attachmentLod != null && attachmentLod.getModelTexture() != null) {
            attachmentLod.setModelTexture(expandTexturePath(attachmentLod.getModelTexture()));
        }
    }

    private static ResourceLocation expandTexturePath(ResourceLocation shortId) {
        return new ResourceLocation(shortId.getNamespace(), "textures/" + shortId.getPath() + ".png");
    }
}

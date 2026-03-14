package com.tacz.legacy.client.resource.pojo.display.attachment;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.legacy.client.resource.pojo.display.LaserConfig;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
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

    @Nullable
    @SerializedName("views")
    private int[] views;

    @SerializedName("scope")
    private boolean isScope = false;

    @SerializedName("sight")
    private boolean isSight = false;

    @SerializedName("fov")
    private float fov = 70;

    @Nullable
    @SerializedName("views_fov")
    private float[] viewsFov;

    @Nullable
    @SerializedName("sounds")
    private Map<String, ResourceLocation> sounds;

    @Nullable
    @SerializedName("laser")
    private LaserConfig laserConfig;

    @SerializedName("text_show")
    private Map<String, TextShow> textShows = Maps.newHashMap();

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

    @Nullable
    public int[] getViews() {
        return views;
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
    public float[] getViewsFov() {
        return viewsFov;
    }

    @Nullable
    public Map<String, ResourceLocation> getSounds() {
        return sounds;
    }

    @Nullable
    public LaserConfig getLaserConfig() {
        return laserConfig;
    }

    public Map<String, TextShow> getTextShows() {
        return textShows;
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

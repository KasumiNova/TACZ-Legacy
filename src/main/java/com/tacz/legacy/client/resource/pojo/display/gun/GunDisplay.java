package com.tacz.legacy.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Gun display POJO — deserialized from gun pack display JSON.
 * Port of upstream TACZ GunDisplay with fields needed for rendering.
 * <p>
 * Texture path conversion (FileToIdConverter equivalent)
 * is handled by {@link #init()} post-deserialization.
 */
public class GunDisplay {
    @SerializedName("model_type")
    private String modelType = "default";
    @SerializedName("model")
    private ResourceLocation modelLocation;
    @SerializedName("texture")
    private ResourceLocation modelTexture;
    @SerializedName("iron_zoom")
    private float ironZoom = 1.2f;
    @SerializedName("zoom_model_fov")
    private float zoomModelFov = 70f;
    @Nullable
    @SerializedName("lod")
    private GunLod gunLod;
    @Nullable
    @SerializedName("hud")
    private ResourceLocation hudTextureLocation;
    @Nullable
    @SerializedName("hud_empty")
    private ResourceLocation hudEmptyTextureLocation;
    @Nullable
    @SerializedName("slot")
    private ResourceLocation slotTextureLocation;
    @Nullable
    @SerializedName("third_person_animation")
    private String thirdPersonAnimation;
    @Nullable
    @SerializedName("animation")
    private ResourceLocation animationLocation;
    @Nullable
    @SerializedName("state_machine")
    private ResourceLocation stateMachineLocation;
    @Nullable
    @SerializedName("state_machine_param")
    private Map<String, Object> stateMachineParam;
    @Nullable
    @SerializedName("sounds")
    private Map<String, ResourceLocation> sounds;
    @Nullable
    @SerializedName("transform")
    private GunTransform transform;
    @SerializedName("show_crosshair")
    private boolean showCrosshair = false;

    public String getModelType() {
        return modelType;
    }

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    @Nullable
    public GunLod getGunLod() {
        return gunLod;
    }

    @Nullable
    public ResourceLocation getHudTextureLocation() {
        return hudTextureLocation;
    }

    @Nullable
    public ResourceLocation getHudEmptyTextureLocation() {
        return hudEmptyTextureLocation;
    }

    @Nullable
    public ResourceLocation getSlotTextureLocation() {
        return slotTextureLocation;
    }

    @Nullable
    public ResourceLocation getAnimationLocation() {
        return animationLocation;
    }

    @Nullable
    public ResourceLocation getStateMachineLocation() {
        return stateMachineLocation;
    }

    @Nullable
    public Map<String, Object> getStateMachineParam() {
        return stateMachineParam;
    }

    @Nullable
    public String getThirdPersonAnimation() {
        return thirdPersonAnimation;
    }

    @Nullable
    public Map<String, ResourceLocation> getSounds() {
        return sounds;
    }

    @Nullable
    public GunTransform getTransform() {
        return transform;
    }

    public float getIronZoom() {
        return ironZoom;
    }

    public float getZoomModelFov() {
        return zoomModelFov;
    }

    public boolean isShowCrosshair() {
        return showCrosshair;
    }

    /**
     * Post-deserialization texture path conversion.
     * Converts short texture names (e.g. {@code tacz:gun/uv/ak47})
     * to full resource paths (e.g. {@code tacz:textures/gun/uv/ak47.png}).
     * Equivalent to upstream's FileToIdConverter("textures", ".png").idToFile().
     */
    public void init() {
        if (modelTexture != null) {
            modelTexture = expandTexturePath(modelTexture);
        }
        if (hudTextureLocation != null) {
            hudTextureLocation = expandTexturePath(hudTextureLocation);
        }
        if (hudEmptyTextureLocation != null) {
            hudEmptyTextureLocation = expandTexturePath(hudEmptyTextureLocation);
        }
        if (slotTextureLocation != null) {
            slotTextureLocation = expandTexturePath(slotTextureLocation);
        }
        if (gunLod != null && gunLod.getModelTexture() != null) {
            gunLod.setModelTexture(expandTexturePath(gunLod.getModelTexture()));
        }
    }

    /**
     * Expands a short texture ID (e.g. {@code tacz:gun/uv/ak47}) to the full
     * resource path ({@code tacz:textures/gun/uv/ak47.png}).
     * Replicates upstream's FileToIdConverter("textures", ".png").idToFile().
     */
    private static ResourceLocation expandTexturePath(ResourceLocation shortId) {
        return new ResourceLocation(shortId.getNamespace(), "textures/" + shortId.getPath() + ".png");
    }
}

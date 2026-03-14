package com.tacz.legacy.client.resource.pojo.display.gun;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.legacy.client.resource.pojo.display.LaserConfig;
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
    @SerializedName("use_default_animation")
    private DefaultAnimationType defaultAnimationType;
    @Nullable
    @SerializedName("default_animation")
    private ResourceLocation defaultAnimation;
    @Nullable
    @SerializedName("player_animator_3rd")
    private ResourceLocation playerAnimator3rd;
    @SerializedName("3rd_fixed_hand")
    private boolean playerAnimator3rdFixedHand = false;
    @Nullable
    @SerializedName("sounds")
    private Map<String, ResourceLocation> sounds;
    @Nullable
    @SerializedName("transform")
    private GunTransform transform;
    @Nullable
    @SerializedName("shell")
    private ShellEjection shellEjection;
    @Nullable
    @SerializedName("ammo")
    private GunAmmo gunAmmo;
    @Nullable
    @SerializedName("muzzle_flash")
    private MuzzleFlash muzzleFlash;
    @Nullable
    @SerializedName("laser")
    private LaserConfig laserConfig;
    @SerializedName("show_crosshair")
    private boolean showCrosshair = false;
    @SerializedName("ammo_count_style")
    private AmmoCountStyle ammoCountStyle = AmmoCountStyle.NORMAL;
    @SerializedName("text_show")
    private Map<String, TextShow> textShows = Maps.newHashMap();

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
    public DefaultAnimationType getDefaultAnimationType() {
        return defaultAnimationType;
    }

    @Nullable
    public ResourceLocation getDefaultAnimation() {
        return defaultAnimation;
    }

    @Nullable
    public ResourceLocation getPlayerAnimator3rd() {
        return playerAnimator3rd;
    }

    public boolean is3rdFixedHand() {
        return playerAnimator3rdFixedHand;
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

    @Nullable
    public ShellEjection getShellEjection() {
        return shellEjection;
    }

    @Nullable
    public GunAmmo getGunAmmo() {
        return gunAmmo;
    }

    @Nullable
    public MuzzleFlash getMuzzleFlash() {
        return muzzleFlash;
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

    public AmmoCountStyle getAmmoCountStyle() {
        return ammoCountStyle;
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
        if (muzzleFlash != null && muzzleFlash.texture != null) {
            muzzleFlash.texture = expandTexturePath(muzzleFlash.texture);
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

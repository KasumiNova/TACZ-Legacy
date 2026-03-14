package com.tacz.legacy.client.resource;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.client.animation.AnimationController;
import com.tacz.legacy.api.client.animation.Animations;
import com.tacz.legacy.api.client.animation.ObjectAnimation;
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.legacy.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.legacy.client.model.BedrockAnimatedModel;
import com.tacz.legacy.client.model.BedrockGunModel;
import com.tacz.legacy.client.resource.gltf.GltfAnimationData;
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.legacy.client.resource.pojo.display.gun.DefaultAnimationType;
import com.tacz.legacy.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.legacy.client.resource.pojo.display.gun.GunLod;
import com.tacz.legacy.client.resource.pojo.display.LaserConfig;
import com.tacz.legacy.client.resource.pojo.display.gun.ShellEjection;
import com.tacz.legacy.client.resource.pojo.display.gun.GunTransform;
import com.tacz.legacy.client.resource.pojo.display.gun.TextShow;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import com.tacz.legacy.sound.SoundManager;
import com.tacz.legacy.util.ColorHex;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 经过处理和校验的枪械显示运行时数据。
 * Port of upstream TACZ GunDisplayInstance for 1.12.2.
 */
public class GunDisplayInstance {
    private static final ResourceLocation DEFAULT_RIFLE_ANIMATION = new ResourceLocation("tacz", "rifle_default");
    private static final ResourceLocation DEFAULT_PISTOL_ANIMATION = new ResourceLocation("tacz", "pistol_default");

    private BedrockGunModel gunModel;
    private @Nullable LodModel lodModel;
    private @Nullable LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
    private @Nullable LuaTable stateMachineParam;
    private Map<String, ResourceLocation> sounds = Maps.newHashMap();
    private @Nullable GunTransform transform;
    private @Nullable ShellEjection shellEjection;
    private ResourceLocation modelTexture;
    private float ironZoom = 1.2f;
    private float zoomModelFov = 70f;
    private boolean showCrosshair = false;
    private AmmoCountStyle ammoCountStyle = AmmoCountStyle.NORMAL;
    private @Nullable LaserConfig laserConfig;

    private GunDisplayInstance() {}

    @Nullable
    public static GunDisplayInstance create(GunDisplay display, TACZClientAssetManager assets) {
        GunDisplayInstance instance = new GunDisplayInstance();
        try {
            instance.checkTextureAndModel(display, assets);
            instance.checkLod(display, assets);
            instance.checkAnimation(display, assets);
            instance.checkSounds(display);
            instance.checkTransform(display);
            instance.checkShellEjection(display);
            instance.checkTextShow(display);
            instance.checkLaserConfig(display);
            instance.ironZoom = Math.max(display.getIronZoom(), 1.0f);
            instance.zoomModelFov = Math.min(display.getZoomModelFov(), 70f);
            instance.showCrosshair = display.isShowCrosshair();
            instance.ammoCountStyle = display.getAmmoCountStyle();
            return instance;
        } catch (Exception e) {
            TACZLegacy.logger.warn("Failed to create GunDisplayInstance: {}", e.getMessage());
            return null;
        }
    }

    private void checkTextureAndModel(GunDisplay display, TACZClientAssetManager assets) {
        ResourceLocation modelLocation = display.getModelLocation();
        if (modelLocation == null) throw new IllegalArgumentException("display missing model");
        TACZClientAssetManager.ModelData modelData = assets.getModel(modelLocation);
        if (modelData == null) throw new IllegalArgumentException("model not found: " + modelLocation);

        ResourceLocation textureLocation = display.getModelTexture();
        if (textureLocation == null) throw new IllegalArgumentException("display missing texture");
        modelTexture = textureLocation;

        gunModel = new BedrockGunModel(modelData.getPojo(), modelData.getVersion());
    }

    private void checkLod(GunDisplay display, TACZClientAssetManager assets) {
        GunLod gunLod = display.getGunLod();
        if (gunLod == null || gunLod.getModelLocation() == null || gunLod.getModelTexture() == null) {
            return;
        }
        TACZClientAssetManager.ModelData modelData = assets.getModel(gunLod.getModelLocation());
        if (modelData == null) {
            return;
        }
        BedrockGunModel model = new BedrockGunModel(modelData.getPojo(), modelData.getVersion());
        lodModel = new LodModel(model, gunLod.getModelTexture());
    }

    private void checkAnimation(GunDisplay display, TACZClientAssetManager assets) {
        ResourceLocation location = display.getAnimationLocation();
        AnimationController controller;
        if (location == null) {
            controller = new AnimationController(Lists.newArrayList(), gunModel);
        } else {
            BedrockAnimationFile animFile = assets.getAnimationFile(location);
            if (animFile != null) {
                controller = Animations.createControllerFromBedrock(animFile, gunModel);
            } else {
                GltfAnimationData gltfAnimation = assets.getGltfAnimation(location);
                if (gltfAnimation == null) {
                    throw new IllegalArgumentException("animation not found: " + location);
                }
                controller = Animations.createControllerFromGltf(gltfAnimation, gunModel);
            }
        }
        ResourceLocation fallbackLocation = resolveFallbackAnimationLocation(display);
        if (fallbackLocation != null) {
            BedrockAnimationFile fallbackFile = assets.getAnimationFile(fallbackLocation);
            if (fallbackFile == null) throw new IllegalArgumentException("animation not found: " + fallbackLocation);
            provideAnimationPrototypesIfAbsent(controller, fallbackFile);
        }
        // Initialize state machine
        ResourceLocation smLocation = display.getStateMachineLocation();
        if (smLocation == null) {
            smLocation = new ResourceLocation("tacz", "default_state_machine");
        }
        LuaTable script = assets.getScript(smLocation);
        if (script != null) {
            animationStateMachine = new LuaStateMachineFactory<GunAnimationStateContext>()
                    .setController(controller)
                    .setLuaScripts(script)
                    .build();
        } else {
            TACZLegacy.logger.warn("State machine script not found: {}, gun will have no animation", smLocation);
        }
        // Load state machine params
        Map<String, Object> params = display.getStateMachineParam();
        if (params != null) {
            stateMachineParam = new LuaTable();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                stateMachineParam.set(entry.getKey(), CoerceJavaToLua.coerce(entry.getValue()));
            }
        }
    }

    @Nullable
    static ResourceLocation resolveFallbackAnimationLocation(GunDisplay display) {
        ResourceLocation explicit = display.getDefaultAnimation();
        if (explicit != null) {
            return explicit;
        }
        return resolveBuiltInDefaultAnimation(display.getDefaultAnimationType());
    }

    @Nullable
    static ResourceLocation resolveBuiltInDefaultAnimation(@Nullable DefaultAnimationType type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case RIFLE:
                return DEFAULT_RIFLE_ANIMATION;
            case PISTOL:
                return DEFAULT_PISTOL_ANIMATION;
            default:
                return null;
        }
    }

    static void provideAnimationPrototypesIfAbsent(AnimationController controller, BedrockAnimationFile animationFile) {
        List<ObjectAnimation> animationList = Animations.createAnimationFromBedrock(animationFile);
        for (ObjectAnimation animation : animationList) {
            controller.providePrototypeIfAbsent(animation.name, () -> new ObjectAnimation(animation));
        }
    }

    private void checkSounds(GunDisplay display) {
        Map<String, ResourceLocation> soundMaps = display.getSounds();
        if (soundMaps != null && !soundMaps.isEmpty()) {
            soundMaps.putIfAbsent(SoundManager.HEAD_HIT_SOUND, new ResourceLocation(TACZLegacy.MOD_ID, SoundManager.HEAD_HIT_SOUND));
            soundMaps.putIfAbsent(SoundManager.FLESH_HIT_SOUND, new ResourceLocation(TACZLegacy.MOD_ID, SoundManager.FLESH_HIT_SOUND));
            soundMaps.putIfAbsent(SoundManager.KILL_SOUND, new ResourceLocation(TACZLegacy.MOD_ID, SoundManager.KILL_SOUND));
            sounds.putAll(soundMaps);
        }
    }

    private void checkTransform(GunDisplay display) {
        GunTransform t = display.getTransform();
        if (t == null || t.getScale() == null) {
            transform = GunTransform.getDefault();
        } else {
            transform = t;
        }
    }

    private void checkShellEjection(GunDisplay display) {
        shellEjection = display.getShellEjection();
        gunModel.setShellEjection(shellEjection);
        if (lodModel != null) {
            lodModel.getModel().setShellEjection(shellEjection);
        }
    }

    private void checkTextShow(GunDisplay display) {
        Map<String, TextShow> textShowMap = collectTextShowMap(display.getTextShows());
        gunModel.setTextShowList(textShowMap);
    }

    private void checkLaserConfig(GunDisplay display) {
        laserConfig = display.getLaserConfig();
    }

    private static Map<String, TextShow> collectTextShowMap(Map<String, TextShow> configuredTextShows) {
        Map<String, TextShow> textShowMap = Maps.newHashMap();
        configuredTextShows.forEach((key, value) -> {
            if (StringUtils.isNoneBlank(key)) {
                value.setColorInt(ColorHex.colorTextToRgbInt(value.getColorText()));
                textShowMap.put(key, value);
            }
        });
        return textShowMap;
    }

    // --- Getters ---

    public BedrockGunModel getGunModel() {
        return gunModel;
    }

    @Nullable
    public LodModel getLodModel() {
        return lodModel;
    }

    /**
     * Set the active gun texture for hand renderers so they can restore it
     * after binding the player skin texture.
     */
    public void setActiveGunTexture(@Nullable ResourceLocation texture) {
        if (gunModel != null) {
            gunModel.setActiveGunTexture(texture);
        }
    }

    @Nullable
    public LuaAnimationStateMachine<GunAnimationStateContext> getAnimationStateMachine() {
        return animationStateMachine;
    }

    @Nullable
    public LuaTable getStateMachineParam() {
        return stateMachineParam;
    }

    @Nullable
    public ResourceLocation getSound(String name) {
        return sounds.get(name);
    }

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

    public ResourceLocation getModelTexture() {
        return modelTexture;
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

    public static final class LodModel {
        private final BedrockGunModel model;
        private final ResourceLocation texture;

        private LodModel(BedrockGunModel model, ResourceLocation texture) {
            this.model = model;
            this.texture = texture;
        }

        public BedrockGunModel getModel() {
            return model;
        }

        public ResourceLocation getTexture() {
            return texture;
        }
    }
}

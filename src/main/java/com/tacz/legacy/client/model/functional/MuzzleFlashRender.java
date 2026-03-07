package com.tacz.legacy.client.model.functional;

import com.tacz.legacy.client.model.BedrockGunModel;
import com.tacz.legacy.client.model.IFunctionalRenderer;
import com.tacz.legacy.client.model.SlotModel;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.pojo.display.gun.MuzzleFlash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Renders a muzzle flash billboard at the "muzzle_flash" bone position.
 * Port of upstream TACZ MuzzleFlashRender, adapted for 1.12.2 GL immediate mode.
 * <p>
 * The flash is displayed for 50ms after a shot, with a random Z rotation
 * and scale-in animation during the first half of the display period.
 */
public class MuzzleFlashRender implements IFunctionalRenderer {
    private static final SlotModel MUZZLE_FLASH_MODEL = new SlotModel(true);
    /** 50ms display time */
    private static final long TIME_RANGE = 50;

    public static boolean isSelf = false;
    private static long shootTimeStamp = -1;
    private static float muzzleFlashRandomRotate = 0;

    /** Set by the renderer before model.render() each frame. */
    @Nullable
    private MuzzleFlash activeMuzzleFlash;

    private final BedrockGunModel bedrockGunModel;

    public MuzzleFlashRender(BedrockGunModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    public static void onShoot() {
        shootTimeStamp = System.currentTimeMillis();
        muzzleFlashRandomRotate = (float) (Math.random() * 360);
    }

    public void setActiveMuzzleFlash(@Nullable MuzzleFlash muzzleFlash) {
        this.activeMuzzleFlash = muzzleFlash;
    }

    @Override
    public void render(int light) {
        if (!isSelf) {
            return;
        }
        long time = System.currentTimeMillis() - shootTimeStamp;
        if (time > TIME_RANGE) {
            return;
        }

        if (activeMuzzleFlash == null) {
            return;
        }
        ResourceLocation texture = activeMuzzleFlash.getTexture();
        if (texture == null) {
            return;
        }
        ResourceLocation registeredTexture = TACZClientAssetManager.INSTANCE.getTextureLocation(texture);
        if (registeredTexture == null) {
            return;
        }

        float scale = 0.5f * activeMuzzleFlash.getScale();
        float scaleTime = TIME_RANGE / 2.0f;
        scale = time < scaleTime ? (scale * (time / scaleTime)) : scale;

        Minecraft.getMinecraft().getTextureManager().bindTexture(registeredTexture);

        // Render translucent background layer
        GlStateManager.pushMatrix();
        {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.rotate(muzzleFlashRandomRotate, 0f, 0f, 1f);
            GlStateManager.translate(0f, -1f, 0f);
            // Full brightness for the flash
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            MUZZLE_FLASH_MODEL.render();
        }
        GlStateManager.popMatrix();

        // Render glow layer (smaller, additive)
        GlStateManager.pushMatrix();
        {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            float glowScale = scale / 2f;
            GlStateManager.scale(glowScale, glowScale, glowScale);
            GlStateManager.rotate(muzzleFlashRandomRotate, 0f, 0f, 1f);
            GlStateManager.translate(0f, -0.9f, 0f);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            MUZZLE_FLASH_MODEL.render();
            // Restore blend func
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
        }
        GlStateManager.popMatrix();
    }
}

package com.tacz.legacy.client.event;

import com.tacz.legacy.api.client.other.KeepingItemRenderer;
import com.tacz.legacy.api.entity.IGunOperator;
import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.api.item.attachment.AttachmentType;
import com.tacz.legacy.client.resource.GunDisplayInstance;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex;
import com.tacz.legacy.common.resource.TACZGunPackPresentation;
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry;
import com.tacz.legacy.util.math.MathUtil;
import com.tacz.legacy.util.math.SecondOrderDynamics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;

public final class FirstPersonFovHooks {
    private static final SecondOrderDynamics WORLD_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0f);
    private static final SecondOrderDynamics ITEM_MODEL_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0f);

    @Nullable
    private static Float lastWorldFov;
    @Nullable
    private static Float lastItemModelFov;

    private FirstPersonFovHooks() {
    }

    public static float applyFovModifier(float originalFov, float partialTicks, boolean useFOVSetting) {
        float resolved = useFOVSetting
                ? applyScopeMagnification(originalFov)
                : applyGunModelFovModifying(originalFov);
        if (useFOVSetting) {
            lastWorldFov = resolved;
        } else {
            lastItemModelFov = resolved;
        }
        return resolved;
    }

    @Nullable
    public static Float getLastWorldFov() {
        return lastWorldFov;
    }

    @Nullable
    public static Float getLastItemModelFov() {
        return lastItemModelFov;
    }

    public static float computeMagnifiedWorldFov(float originalFov, float zoom, float aimingProgress) {
        float clampedZoom = Math.max(1.0f, zoom);
        float clampedProgress = MathHelper.clamp(aimingProgress, 0.0f, 1.0f);
        return (float) MathUtil.magnificationToFov(1.0f + (clampedZoom - 1.0f) * clampedProgress, originalFov);
    }

    public static float blendItemModelFov(float originalFov, float modifiedFov, float aimingProgress) {
        float clampedProgress = MathHelper.clamp(aimingProgress, 0.0f, 1.0f);
        return originalFov + (modifiedFov - originalFov) * clampedProgress;
    }

    public static float resolveItemModelFovTarget(@Nullable float[] viewsFov, int zoomNumber, @Nullable Float zoomModelFov, float originalFov) {
        if (viewsFov != null && viewsFov.length > 0) {
            return viewsFov[Math.floorMod(zoomNumber, viewsFov.length)];
        }
        if (zoomModelFov != null && zoomModelFov > 0.0f) {
            return zoomModelFov;
        }
        return originalFov;
    }

    private static float applyScopeMagnification(float originalFov) {
        EntityLivingBase living = getRenderViewLivingEntity();
        if (living == null) {
            return WORLD_FOV_DYNAMICS.update(originalFov);
        }
        ItemStack stack = getRenderedMainItem(living);
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            return WORLD_FOV_DYNAMICS.update(originalFov);
        }
        float zoom = iGun.getAimingZoom(stack);
        float aimingProgress = resolveAimingProgress(living);
        return WORLD_FOV_DYNAMICS.update(computeMagnifiedWorldFov(originalFov, zoom, aimingProgress));
    }

    private static float applyGunModelFovModifying(float originalFov) {
        EntityLivingBase living = getRenderViewLivingEntity();
        if (living == null) {
            return ITEM_MODEL_FOV_DYNAMICS.update(originalFov);
        }
        ItemStack stack = getRenderedMainItem(living);
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            return ITEM_MODEL_FOV_DYNAMICS.update(originalFov);
        }
        float modifiedFov = resolveGunItemModelFovTarget(iGun, stack, originalFov);
        float aimingProgress = resolveAimingProgress(living);
        return ITEM_MODEL_FOV_DYNAMICS.update(blendItemModelFov(originalFov, modifiedFov, aimingProgress));
    }

    private static float resolveGunItemModelFovTarget(IGun iGun, ItemStack stack, float originalFov) {
        ItemStack scopeStack = iGun.getAttachment(stack, AttachmentType.SCOPE);
        if (scopeStack.isEmpty()) {
            scopeStack = iGun.getBuiltinAttachment(stack, AttachmentType.SCOPE);
        }
        if (!scopeStack.isEmpty()) {
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(scopeStack);
            if (iAttachment != null) {
                ClientAttachmentIndex attachmentIndex = TACZClientAssetManager.INSTANCE.getAttachmentIndex(iAttachment.getAttachmentId(scopeStack));
                if (attachmentIndex != null) {
                    return resolveItemModelFovTarget(
                            attachmentIndex.getViewsFov(),
                            iAttachment.getZoomNumber(scopeStack),
                            null,
                            originalFov
                    );
                }
            }
        }

        ResourceLocation displayId = TACZGunPackPresentation.INSTANCE.resolveGunDisplayId(
                TACZGunPackRuntimeRegistry.currentSnapshotForJava(),
                iGun.getGunId(stack)
        );
        GunDisplayInstance displayInstance = displayId == null ? null : TACZClientAssetManager.INSTANCE.getGunDisplayInstance(displayId);
        Float zoomModelFov = displayInstance == null ? null : displayInstance.getZoomModelFov();
        return resolveItemModelFovTarget(null, 0, zoomModelFov, originalFov);
    }

    @Nullable
    private static EntityLivingBase getRenderViewLivingEntity() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return null;
        }
        Entity entity = minecraft.getRenderViewEntity();
        return entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
    }

    private static ItemStack getRenderedMainItem(EntityLivingBase living) {
        if (living instanceof EntityPlayerSP && KeepingItemRenderer.getRenderer() != null) {
            ItemStack currentItem = KeepingItemRenderer.getRenderer().getCurrentItem();
            if (currentItem != null && !currentItem.isEmpty()) {
                return currentItem;
            }
        }
        return living.getHeldItemMainhand();
    }

    private static float resolveAimingProgress(EntityLivingBase living) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && living == minecraft.player) {
            Float preparedAiming = FirstPersonRenderGunEvent.getPreparedAimingProgress();
            if (preparedAiming != null) {
                return MathHelper.clamp(preparedAiming, 0.0f, 1.0f);
            }
            return MathHelper.clamp(FirstPersonRenderGunEvent.getLastPreparedAimingProgress(), 0.0f, 1.0f);
        }
        return MathHelper.clamp(IGunOperator.fromLivingEntity(living).getSynAimingProgress(), 0.0f, 1.0f);
    }
}
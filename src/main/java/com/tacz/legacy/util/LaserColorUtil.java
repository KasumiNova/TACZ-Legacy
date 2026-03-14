package com.tacz.legacy.util;

import com.tacz.legacy.api.item.IAttachment;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.client.resource.GunDisplayInstance;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.client.resource.index.ClientAttachmentIndex;
import com.tacz.legacy.client.resource.pojo.display.LaserConfig;
import com.tacz.legacy.common.resource.TACZGunPackPresentation;
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * Resolves the effective laser color for guns / attachments.
 *
 * Priority matches upstream TACZ semantics:
 * custom item NBT color -> structured LaserConfig default -> hardcoded red.
 */
public final class LaserColorUtil {
    private static final int DEFAULT_LASER_COLOR = 0xFF0000;

    private LaserColorUtil() {
    }

    public static int getLaserColor(ItemStack stack, @Nonnull LaserConfig defaultConfig) {
        if (stack == null || stack.isEmpty()) {
            return defaultConfig.getDefaultColor();
        }

        if (stack.getItem() instanceof IAttachment) {
            IAttachment attachment = (IAttachment) stack.getItem();
            return attachment.hasCustomLaserColor(stack)
                    ? attachment.getLaserColor(stack)
                    : defaultConfig.getDefaultColor();
        }

        if (stack.getItem() instanceof IGun) {
            IGun gun = (IGun) stack.getItem();
            return gun.hasCustomLaserColor(stack)
                    ? gun.getLaserColor(stack)
                    : defaultConfig.getDefaultColor();
        }

        return defaultConfig.getDefaultColor();
    }

    public static int getLaserColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_LASER_COLOR;
        }

        if (stack.getItem() instanceof IAttachment) {
            IAttachment attachment = (IAttachment) stack.getItem();
            if (attachment.hasCustomLaserColor(stack)) {
                return attachment.getLaserColor(stack);
            }
            ClientAttachmentIndex index = TACZClientAssetManager.INSTANCE.getAttachmentIndex(attachment.getAttachmentId(stack));
            return index != null && index.getLaserConfig() != null
                    ? index.getLaserConfig().getDefaultColor()
                    : DEFAULT_LASER_COLOR;
        }

        if (stack.getItem() instanceof IGun) {
            IGun gun = (IGun) stack.getItem();
            if (gun.hasCustomLaserColor(stack)) {
                return gun.getLaserColor(stack);
            }
                ResourceLocation displayId = TACZGunPackPresentation.INSTANCE.resolveGunDisplayId(
                        TACZGunPackRuntimeRegistry.currentSnapshotForJava(),
                    gun.getGunId(stack)
            );
            if (displayId == null) {
                return DEFAULT_LASER_COLOR;
            }
            GunDisplayInstance displayInstance = TACZClientAssetManager.INSTANCE.getGunDisplayInstance(displayId);
            return displayInstance != null && displayInstance.getLaserConfig() != null
                    ? displayInstance.getLaserConfig().getDefaultColor()
                    : DEFAULT_LASER_COLOR;
        }

        return DEFAULT_LASER_COLOR;
    }
}
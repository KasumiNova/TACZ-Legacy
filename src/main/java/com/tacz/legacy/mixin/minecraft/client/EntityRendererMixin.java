package com.tacz.legacy.mixin.minecraft.client;

import com.tacz.legacy.api.client.other.KeepingItemRenderer;
import com.tacz.legacy.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Matches upstream TACZ item-in-hand bob suppression for first-person gun rendering.
 *
 * Vanilla 1.12 applies hand bobbing inside EntityRenderer#renderHand before ItemRenderer.
 * Legacy's custom gun renderer caches muzzle offsets from that hand render, while tracer
 * reconstruction only replays camera yaw/pitch. Skipping the vanilla bob on gun hand renders
 * keeps the cached muzzle offset in the same camera space expected by the tracer renderer.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow
    private void applyBobbing(float partialTicks) {
    }

    @Redirect(
            method = "renderHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;applyBobbing(F)V",
                    ordinal = 0
            )
    )
    private void tacz$skipGunItemHandBobbing(EntityRenderer instance, float partialTicks) {
        if (!tacz$shouldSkipHandBobbing()) {
            applyBobbing(partialTicks);
        }
    }

    private boolean tacz$shouldSkipHandBobbing() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.gameSettings.thirdPersonView != 0) {
            return false;
        }
        ItemStack renderedMainItem = KeepingItemRenderer.getRenderer() != null
                ? KeepingItemRenderer.getRenderer().getCurrentItem()
                : player.getHeldItemMainhand();
        return renderedMainItem.getItem() instanceof IGun;
    }
}
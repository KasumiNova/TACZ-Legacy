package com.tacz.legacy.mixin.minecraft.client;

import com.tacz.legacy.client.render.camera.WeaponFovController;
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Shadow
    @Final
    private Minecraft mc;

    @Unique
    private boolean tacz$insideRenderHand = false;

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void tacz$beginRenderHand(float partialTicks, int pass, CallbackInfo ci) {
        this.tacz$insideRenderHand = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void tacz$endRenderHand(float partialTicks, int pass, CallbackInfo ci) {
        this.tacz$insideRenderHand = false;
    }

    @Inject(method = "applyBobbing", at = @At("HEAD"), cancellable = true)
    private void tacz$cancelVanillaHandBobbing(float partialTicks, CallbackInfo ci) {
        if (!this.tacz$insideRenderHand) {
            return;
        }
        if (this.mc == null || this.mc.player == null) {
            return;
        }
        if (this.mc.gameSettings.thirdPersonView != 0) {
            return;
        }

        ItemStack mainHand = this.mc.player.getHeldItemMainhand();
        if (!mainHand.isEmpty() && mainHand.getItem() instanceof LegacyGunItem) {
            ci.cancel();
        }
    }

    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void tacz$applyLegacyGunFovModifier(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        if (this.mc == null) {
            return;
        }

        Float currentFov = cir.getReturnValue();
        if (currentFov == null) {
            return;
        }

        float adjustedFov = WeaponFovController.modifyFov(
            this.mc,
            partialTicks,
            useFOVSetting,
            currentFov
        );
        cir.setReturnValue(adjustedFov);
    }
}

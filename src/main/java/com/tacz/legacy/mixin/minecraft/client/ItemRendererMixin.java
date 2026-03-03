package com.tacz.legacy.mixin.minecraft.client;

import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    private ItemStack itemStackMainHand;

    @Shadow
    private float equippedProgressMainHand;

    @Shadow
    private float prevEquippedProgressMainHand;

    @Inject(method = "updateEquippedItem", at = @At("TAIL"))
    private void tacz$forceMainHandEquipStableForGun(CallbackInfo ci) {
        if (this.mc == null) {
            return;
        }
        EntityPlayerSP player = this.mc.player;
        if (player == null) {
            return;
        }
        ItemStack mainHand = player.getHeldItemMainhand();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof LegacyGunItem)) {
            return;
        }

        this.itemStackMainHand = mainHand;
        this.equippedProgressMainHand = 1.0F;
        this.prevEquippedProgressMainHand = 1.0F;
    }
}

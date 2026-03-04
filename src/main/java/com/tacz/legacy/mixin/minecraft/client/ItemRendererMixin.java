package com.tacz.legacy.mixin.minecraft.client;

import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    @ModifyVariable(
            method = "renderItemInFirstPerson(Lnet/minecraft/client/entity/AbstractClientPlayer;FFLnet/minecraft/util/EnumHand;FLnet/minecraft/item/ItemStack;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4
    )
    private float tacz$disableSwingProgressForGun(
            float swingProgress,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            EnumHand hand,
            ItemStack stack,
            float equipProgress
    ) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof LegacyGunItem) {
            return 0.0F;
        }
        return swingProgress;
    }
}

package com.tacz.legacy.client.animation.statemachine;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.entity.IGunOperator;
import com.tacz.legacy.api.entity.ReloadState;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.api.item.attachment.AttachmentType;
import com.tacz.legacy.api.item.gun.FireMode;
import com.tacz.legacy.client.model.BedrockGunModel;
import com.tacz.legacy.client.model.functional.ShellRender;
import com.tacz.legacy.client.resource.GunDisplayInstance;
import com.tacz.legacy.client.resource.pojo.display.gun.ShellEjection;
import com.tacz.legacy.common.resource.BoltType;
import com.tacz.legacy.common.resource.GunCombatData;
import com.tacz.legacy.common.resource.GunDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

/**
 * 枪械动画状态上下文 — Lua 脚本通过此上下文查询枪械/玩家状态。
 * Port of upstream TACZ GunAnimationStateContext, adapted for 1.12.2 APIs.
 */
@SuppressWarnings("unused")
public class GunAnimationStateContext extends ItemAnimationStateContext {
    private static final String HEAT_AMOUNT_TAG = "HeatAmount";

    private ItemStack currentGunItem = ItemStack.EMPTY;
    private @Nullable IGun iGun;
    private @Nullable GunDisplayInstance display;
    private @Nullable GunCombatData gunData;
    private float walkDistAnchor = 0f;

    private <T> Optional<T> processGunData(java.util.function.BiFunction<IGun, GunDisplayInstance, T> processor) {
        if (iGun != null && display != null) {
            return Optional.ofNullable(processor.apply(iGun, display));
        }
        return Optional.empty();
    }

    private <T> Optional<T> processGunOperator(Function<IGunOperator, T> processor) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player instanceof IGunOperator) {
            return Optional.ofNullable(processor.apply((IGunOperator) player));
        }
        return Optional.empty();
    }

    private <T> Optional<T> processCameraEntity(Function<Entity, T> processor) {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        if (entity != null) {
            return Optional.ofNullable(processor.apply(entity));
        }
        return Optional.empty();
    }

    public boolean hasBulletInBarrel() {
        return processGunData((iGun, display) -> {
            if (gunData != null && gunData.getBoltType() == BoltType.OPEN_BOLT) {
                return false;
            }
            return iGun.hasBulletInBarrel(currentGunItem);
        }).orElse(false);
    }

    public boolean isOverHeat() {
        return gunData != null
                && gunData.getHasHeatData()
                && iGun != null
                && iGun.isOverheatLocked(currentGunItem);
    }

    public long getShootInterval() {
        if (gunData != null) {
            FireMode fireMode = iGun != null ? iGun.getFireMode(currentGunItem) : FireMode.UNKNOWN;
            if (fireMode == FireMode.BURST && gunData.getBurstMinInterval() > 0) {
                return Math.max((long) (gunData.getBurstMinInterval() * 1000f), 0L);
            }
            return Math.max(gunData.getShootIntervalMs(), 0L);
        }
        return 0L;
    }

    public long getLastShootTimestamp() {
        return com.tacz.legacy.client.gameplay.LegacyClientShootCoordinator.INSTANCE.getClientLastShootTimestampMs();
    }

    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    public void adjustClientShootInterval(long alpha) {
        com.tacz.legacy.client.gameplay.LegacyClientShootCoordinator.INSTANCE.adjustClientShootTimestamp(alpha);
    }

    public int getAmmoCount() {
        return processGunData((iGun, display) -> iGun.getCurrentAmmoCount(currentGunItem)).orElse(0);
    }

    public int getMaxAmmoCount() {
        if (gunData != null) {
            return gunData.getAmmoAmount();
        }
        return 0;
    }

    public boolean hasAmmoToConsume() {
        // Simplified: check if player has any ammo
        Boolean needCheck = processGunOperator(IGunOperator::needCheckAmmo).orElse(true);
        if (!needCheck) return true;
        if (iGun != null && iGun.useDummyAmmo(currentGunItem)) {
            return iGun.getDummyAmmoAmount(currentGunItem) > 0;
        }
        return processCameraEntity(entity -> {
            if (entity instanceof EntityLivingBase) {
                return iGun != null && iGun.hasInventoryAmmo((EntityLivingBase) entity, currentGunItem, true);
            }
            return false;
        }).orElse(false);
    }

    public int getMagExtentLevel() {
        if (iGun == null) {
            return 0;
        }
        ResourceLocation attachmentId = iGun.getAttachmentId(currentGunItem, AttachmentType.EXTENDED_MAG);
        return MathHelper.clamp(GunDataAccessor.getAttachmentExtendedMagLevel(attachmentId), 0, 3);
    }

    public int getFireMode() {
        return processGunData((iGun, display) -> iGun.getFireMode(currentGunItem).ordinal()).orElse(0);
    }

    public float getAimingProgress() {
        return processGunOperator(op -> op.getSynAimingProgress()).orElse(0f);
    }

    public boolean isAiming() {
        return processGunOperator(op -> op.getSynIsAiming()).orElse(false);
    }

    public long getShootCoolDown() {
        return processGunOperator(op -> op.getSynShootCoolDown()).orElse(0L);
    }

    public int getReloadStateType() {
        return processCameraEntity(entity -> {
            if (entity instanceof IGunOperator) {
                return ((IGunOperator) entity).getSynReloadState().getStateType().ordinal();
            }
            return ReloadState.StateType.NOT_RELOADING.ordinal();
        }).orElse(ReloadState.StateType.NOT_RELOADING.ordinal());
    }

    public boolean isInputUp() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput != null && player.movementInput.forwardKeyDown;
    }

    public boolean isInputDown() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput != null && player.movementInput.backKeyDown;
    }

    public boolean isInputLeft() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput != null && player.movementInput.leftKeyDown;
    }

    public boolean isInputRight() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput != null && player.movementInput.rightKeyDown;
    }

    public boolean isInputJumping() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput != null && player.movementInput.jump;
    }

    public boolean isCrawl() {
        return processGunOperator(op -> op.getDataHolder().isCrawling).orElse(false);
    }

    public boolean isOnGround() {
        return processCameraEntity(entity -> entity.onGround).orElse(false);
    }

    public boolean isCrouching() {
        return processCameraEntity(Entity::isSneaking).orElse(false);
    }

    public boolean shouldSlide() {
        return processCameraEntity(entity -> gunData != null && entity.isSneaking() && gunData.canSlide()).orElse(false);
    }

    public void anchorWalkDist() {
        processCameraEntity(entity -> {
            walkDistAnchor = entity.distanceWalkedModified
                    + (entity.distanceWalkedModified - entity.prevDistanceWalkedModified) * partialTicks;
            return null;
        });
    }

    public float getWalkDist() {
        return processCameraEntity(entity -> {
            float currentWalkDist = entity.distanceWalkedModified
                    + (entity.distanceWalkedModified - entity.prevDistanceWalkedModified) * partialTicks;
            return currentWalkDist - walkDistAnchor;
        }).orElse(0f);
    }

    public void popShellFrom(int index) {
        if (display == null) {
            logFocusedSmokeShellSkipped(index, "display_missing");
            return;
        }
        ShellEjection shellEjection = display.getShellEjection();
        if (shellEjection == null) {
            logFocusedSmokeShellSkipped(index, "shell_ejection_missing");
            return;
        }
        BedrockGunModel gunModel = display.getGunModel();
        if (gunModel == null) {
            logFocusedSmokeShellSkipped(index, "gun_model_missing");
            return;
        }
        Vector3f velocity = shellEjection.getRandomVelocity();
        ShellRender shellRender = gunModel.getShellRender(index);
        boolean mainAdded = false;
        int mainActive = 0;
        if (shellRender != null) {
            shellRender.addShell(velocity);
            mainAdded = true;
            mainActive = shellRender.getActiveShellCount();
        }

        boolean lodAdded = false;
        int lodActive = 0;
        GunDisplayInstance.LodModel lodModel = display.getLodModel();
        if (lodModel != null) {
            ShellRender lodShellRender = lodModel.getModel().getShellRender(index);
            if (lodShellRender != null) {
                lodShellRender.addShell(velocity);
                lodAdded = true;
                lodActive = lodShellRender.getActiveShellCount();
            }
        }

        logFocusedSmokeShellPop(index, shellEjection, mainAdded, lodAdded, mainActive, lodActive);
    }

    public LuaTable getStateMachineParams() {
        if (display != null) {
            LuaTable param = display.getStateMachineParam();
            return param == null ? new LuaTable() : param;
        }
        return new LuaTable();
    }

    public String getAttachment(String type) {
        try {
            AttachmentType t = AttachmentType.valueOf(type);
            if (iGun != null) {
                return iGun.getAttachmentId(currentGunItem, t).toString();
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "tacz:empty";
    }

    public float getHeatProgress() {
        if (gunData == null || !gunData.getHasHeatData()) {
            return 0f;
        }
        float heatMax = gunData.getHeatMax();
        if (heatMax <= 0f) {
            return 0f;
        }
        float heatAmount = currentGunItem.hasTagCompound() ? currentGunItem.getTagCompound().getFloat(HEAT_AMOUNT_TAG) : 0f;
        return MathHelper.clamp(heatAmount / heatMax, 0f, 1f);
    }

    /**
     * 状态机脚本请不要调用此方法。此方法用于状态机更新时设置当前的物品对象。
     */
    public void setCurrentGunItem(ItemStack currentGunItem) {
        this.currentGunItem = currentGunItem;
        this.iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(currentGunItem);
            gunData = GunDataAccessor.getGunData(gunId);
        } else {
            gunData = null;
        }
    }

    public void setDisplay(@Nullable GunDisplayInstance display) {
        this.display = display;
    }

    private void logFocusedSmokeShellPop(int index, ShellEjection shellEjection, boolean mainAdded, boolean lodAdded, int mainActive, int lodActive) {
        if (!isFocusedSmokeEnabled()) {
            return;
        }
        ResourceLocation gunId = iGun != null ? iGun.getGunId(currentGunItem) : null;
        TACZLegacy.logger.info(
                "[FocusedSmoke] SHELL_POP gun={} index={} mainAdded={} lodAdded={} mainActive={} lodActive={} lifetimeMs={} initialVelocity={} randomVelocity={} acceleration={} angularVelocity={}",
                gunId != null ? gunId : "unknown",
                index,
                mainAdded,
                lodAdded,
                mainActive,
                lodActive,
                Math.max((long) (shellEjection.getLivingTime() * 1000.0f), 0L),
                formatVector(shellEjection.getInitialVelocity()),
                formatVector(shellEjection.getRandomVelocity()),
                formatVector(shellEjection.getAcceleration()),
                formatVector(shellEjection.getAngularVelocity())
        );
    }

    private void logFocusedSmokeShellSkipped(int index, String reason) {
        if (!isFocusedSmokeEnabled()) {
            return;
        }
        ResourceLocation gunId = iGun != null ? iGun.getGunId(currentGunItem) : null;
        TACZLegacy.logger.info(
                "[FocusedSmoke] SHELL_POP_SKIPPED gun={} index={} reason={}",
                gunId != null ? gunId : "unknown",
                index,
                reason
        );
    }

    private static boolean isFocusedSmokeEnabled() {
        return Boolean.parseBoolean(System.getProperty("tacz.focusedSmoke", "false"));
    }

    private static String formatVector(Vector3f vector) {
        return String.format("[%.3f,%.3f,%.3f]", vector.x(), vector.y(), vector.z());
    }
}

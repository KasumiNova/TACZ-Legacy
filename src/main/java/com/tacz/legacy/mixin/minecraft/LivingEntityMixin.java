package com.tacz.legacy.mixin.minecraft;

import com.tacz.legacy.api.entity.IGunOperator;
import com.tacz.legacy.api.entity.KnockBackModifier;
import com.tacz.legacy.api.entity.ReloadState;
import com.tacz.legacy.api.entity.ShootResult;
import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.common.entity.shooter.*;
import com.tacz.legacy.common.resource.GunDataAccessor;
import com.tacz.legacy.common.resource.GunCombatData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 将 IGunOperator 和 KnockBackModifier 接口注入到所有 EntityLivingBase 上。
 * 对应上游 TACZ 的 LivingEntityMixin。
 */
@Mixin(EntityLivingBase.class)
public abstract class LivingEntityMixin implements IGunOperator, KnockBackModifier {

    @Unique
    private ShooterDataHolder tacz$dataHolder;

    @Unique
    private LivingEntityDrawGun tacz$drawGun;

    @Unique
    private LivingEntityShoot tacz$shoot;

    @Unique
    private LivingEntityReload tacz$reload;

    @Unique
    private LivingEntityAim tacz$aim;

    @Unique
    private LivingEntityBolt tacz$bolt;

    @Unique
    private LivingEntityFireSelect tacz$fireSelect;

    @Unique
    private LivingEntityMelee tacz$melee;

    @Unique
    private LivingEntitySprint tacz$sprint;

    @Unique
    private LivingEntityAmmoCheck tacz$ammoCheck;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void tacz$onInit(CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        tacz$dataHolder = new ShooterDataHolder();
        tacz$drawGun = new LivingEntityDrawGun(self, tacz$dataHolder);
        tacz$shoot = new LivingEntityShoot(self, tacz$dataHolder, tacz$drawGun);
        tacz$reload = new LivingEntityReload(self, tacz$dataHolder, tacz$drawGun);
        tacz$aim = new LivingEntityAim(self, tacz$dataHolder, tacz$drawGun);
        tacz$bolt = new LivingEntityBolt(self, tacz$dataHolder, tacz$drawGun);
        tacz$fireSelect = new LivingEntityFireSelect(self, tacz$dataHolder);
        tacz$melee = new LivingEntityMelee(self, tacz$dataHolder, tacz$drawGun);
        tacz$sprint = new LivingEntitySprint(self, tacz$dataHolder);
        tacz$ammoCheck = new LivingEntityAmmoCheck(self, tacz$dataHolder);
    }

    // ---- IGunOperator ----

    @Override
    public long getSynShootCoolDown() {
        return tacz$shoot.getShootCoolDown();
    }

    @Override
    public long getSynMeleeCoolDown() {
        return tacz$melee.getMeleeCoolDown();
    }

    @Override
    public long getSynDrawCoolDown() {
        return tacz$drawGun.getDrawCoolDown();
    }

    @Override
    public boolean getSynIsBolting() {
        return tacz$dataHolder.isBolting;
    }

    @Override
    public ReloadState getSynReloadState() {
        return tacz$reload.getReloadState();
    }

    @Override
    public float getSynAimingProgress() {
        return tacz$dataHolder.aimingProgress;
    }

    @Override
    public boolean getSynIsAiming() {
        return tacz$dataHolder.isAiming;
    }

    @Override
    public float getSynSprintTime() {
        return tacz$dataHolder.sprintTimeS;
    }

    @Override
    public void initialData() {
        tacz$dataHolder.initialData();
    }

    @Override
    public void draw(Supplier<ItemStack> gunItemSupplier) {
        tacz$drawGun.draw(gunItemSupplier);
    }

    @Override
    public void bolt() {
        tacz$bolt.bolt();
    }

    @Override
    public void reload() {
        tacz$reload.reload();
    }

    @Override
    public void cancelReload() {
        tacz$reload.cancelReload();
    }

    @Override
    public void fireSelect() {
        tacz$fireSelect.fireSelect();
    }

    @Override
    public void aim(boolean isAiming) {
        tacz$aim.aim(isAiming);
    }

    @Override
    public void melee() {
        tacz$melee.melee();
    }

    @Override
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp) {
        return tacz$shoot.shoot(pitch, yaw, timestamp);
    }

    @Override
    public boolean needCheckAmmo() {
        return tacz$ammoCheck.needCheckAmmo();
    }

    @Override
    public boolean consumesAmmoOrNot() {
        return tacz$ammoCheck.consumesAmmoOrNot();
    }

    @Override
    public float getProcessedSprintStatus() {
        return tacz$sprint.getProcessedSprintStatus();
    }

    @Override
    public ShooterDataHolder getDataHolder() {
        return tacz$dataHolder;
    }

    @Override
    public void tick() {
        tacz$reload.tickReload();
        tacz$bolt.tickBolt();
        tacz$aim.tickAimingProgress();
        tacz$melee.tickMelee();
        tacz$sprint.tickSprint();
        tacz$tickHeat();
    }

    @Unique
    private void tacz$tickHeat() {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // 与上游 TACZ 一致：热量计算只在服务端执行，客户端通过 ItemStack NBT 同步获取
        if (self.world.isRemote) return;
        ItemStack mainHand = self.getHeldItemMainhand();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof IGun)) return;
        IGun iGun = (IGun) mainHand.getItem();
        GunCombatData gunData = GunDataAccessor.getGunData(iGun.getGunId(mainHand));
        if (gunData == null || !gunData.getHasHeatData()) return;

        long heatTs = tacz$dataHolder.heatTimestamp;
        if (heatTs < 0) return;

        // 脚本 hook: tick_heat
        org.luaj.vm2.LuaTable script = com.tacz.legacy.common.entity.shooter.TACZGunScriptAPI.Companion.resolveScript(gunData);
        org.luaj.vm2.LuaFunction tickHeatFunc = script != null
                ? com.tacz.legacy.common.entity.shooter.TACZGunScriptAPI.Companion.checkFunction(script, "tick_heat")
                : null;
        if (tickHeatFunc != null) {
            com.tacz.legacy.common.entity.shooter.TACZGunScriptAPI api =
                    com.tacz.legacy.common.entity.shooter.TACZGunScriptAPI.Companion.create(self, tacz$dataHolder, mainHand, null, null);
            tickHeatFunc.call(
                    org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(api),
                    org.luaj.vm2.LuaValue.valueOf(heatTs)
            );
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - heatTs;
        boolean locked = iGun.isOverheatLocked(mainHand);

        if (locked) {
            // 过热锁定：等待 overHeatTime 后开始冷却
            if (elapsed < gunData.getHeatOverHeatTimeMs()) return;
            float cooling = (float) elapsed / 10000.0f * gunData.getHeatCoolingMultiplier();
            float current = iGun.getHeatAmount(mainHand);
            float newHeat = current - cooling;
            iGun.setHeatAmount(mainHand, Math.max(newHeat, 0));
            if (newHeat <= 0) {
                iGun.setOverheatLocked(mainHand, false);
            }
        } else {
            // 正常冷却：等待 coolingDelay 后开始冷却
            float current = iGun.getHeatAmount(mainHand);
            if (current <= 0) return;
            if (elapsed < gunData.getHeatCoolingDelayMs()) return;
            float cooling = (float) elapsed / 10000.0f * gunData.getHeatCoolingMultiplier();
            float newHeat = current - cooling;
            iGun.setHeatAmount(mainHand, Math.max(newHeat, 0));
        }
    }

    // ---- KnockBackModifier ----

    @Override
    public void resetKnockBackStrength() {
        tacz$dataHolder.knockbackStrength = -1.0;
    }

    @Override
    public double getKnockBackStrength() {
        return tacz$dataHolder.knockbackStrength;
    }

    @Override
    public void setKnockBackStrength(double strength) {
        tacz$dataHolder.knockbackStrength = strength;
    }
}

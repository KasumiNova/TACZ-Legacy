package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.entity.EntityKineticBullet
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.common.resource.TACZGunPropertyResolver
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua

internal object TACZBulletSpreadResolver {
    internal fun resolveBulletAmount(gunItem: net.minecraft.item.ItemStack, iGun: IGun, gunData: GunCombatData): Int {
        return TACZGunPropertyResolver.resolveBulletAmount(gunItem, iGun, gunData)
    }

    internal fun applySpread(
        shooter: net.minecraft.entity.EntityLivingBase,
        dataHolder: ShooterDataHolder,
        gunItem: net.minecraft.item.ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
        bullet: EntityKineticBullet,
        bulletIndex: Int,
        processedSpeed: Float,
        pitch: Float,
        yaw: Float,
        scriptApi: TACZGunScriptAPI? = null,
    ) {
        val inaccuracy = TACZGunPropertyResolver.resolveInaccuracy(shooter, gunItem, iGun, gunData)
        if (applyScriptSpread(scriptApi, shooter, dataHolder, gunItem, gunData, bullet, bulletIndex, processedSpeed, inaccuracy, pitch, yaw)) {
            return
        }
        bullet.shootFromRotation(shooter, pitch, yaw, processedSpeed, inaccuracy)
    }

    private fun applyScriptSpread(
        existingApi: TACZGunScriptAPI?,
        shooter: net.minecraft.entity.EntityLivingBase,
        dataHolder: ShooterDataHolder,
        gunItem: net.minecraft.item.ItemStack,
        gunData: GunCombatData,
        bullet: EntityKineticBullet,
        bulletIndex: Int,
        processedSpeed: Float,
        inaccuracy: Float,
        pitch: Float,
        yaw: Float,
    ): Boolean {
        val script = TACZGunScriptAPI.resolveScript(gunData) ?: return false
        val function = TACZGunScriptAPI.checkFunction(script, "calcSpread") ?: return false
        val api = existingApi ?: TACZGunScriptAPI.create(shooter, dataHolder, gunItem)
        val luaValue = function.call(
            CoerceJavaToLua.coerce(api),
            LuaValue.valueOf(bulletIndex),
            LuaValue.valueOf(inaccuracy.toDouble()),
        )
        if (!luaValue.istable()) {
            return false
        }
        val table = luaValue.checktable()
        val spreadX = table.get(1).checkdouble()
        val spreadY = table.get(2).checkdouble()
        bullet.shootFromRotation(shooter, pitch, yaw, processedSpeed, spreadX, spreadY)
        return true
    }
}
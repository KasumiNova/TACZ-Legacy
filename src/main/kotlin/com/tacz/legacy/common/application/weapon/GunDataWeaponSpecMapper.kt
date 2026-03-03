package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.domain.gunpack.GunData
import com.tacz.legacy.common.domain.gunpack.GunFireMode
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import com.tacz.legacy.common.domain.weapon.WeaponSpec
import kotlin.math.max
import kotlin.math.roundToInt

public object GunDataWeaponSpecMapper {

    public fun toWeaponSpec(
        gunData: GunData,
        ticksPerSecond: Int = 20
    ): WeaponSpec {
        require(ticksPerSecond > 0) { "ticksPerSecond must be > 0, got $ticksPerSecond" }

        val fireMode = when {
            gunData.fireModes.contains(GunFireMode.AUTO) -> WeaponFireMode.AUTO
            gunData.fireModes.contains(GunFireMode.BURST) -> WeaponFireMode.BURST
            else -> WeaponFireMode.SEMI
        }

        val reloadSeconds = when {
            gunData.reload.emptyTimeSeconds > 0f -> gunData.reload.emptyTimeSeconds
            gunData.reload.tacticalTimeSeconds > 0f -> gunData.reload.tacticalTimeSeconds
            else -> 0.05f
        }

        val reloadTicks = max(1, (reloadSeconds * ticksPerSecond).roundToInt())
        val ballisticDistance = max(8.0, gunData.bullet.lifeSeconds.toDouble() * gunData.bullet.speed.toDouble())

        return WeaponSpec(
            magazineSize = max(1, gunData.ammoAmount),
            roundsPerMinute = max(1, gunData.roundsPerMinute),
            reloadTicks = reloadTicks,
            fireMode = fireMode,
            maxDistance = ballisticDistance
        )
    }

}
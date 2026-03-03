package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.domain.gunpack.GunBoltType
import com.tacz.legacy.common.domain.gunpack.GunBulletData
import com.tacz.legacy.common.domain.gunpack.GunData
import com.tacz.legacy.common.domain.gunpack.GunFeedType
import com.tacz.legacy.common.domain.gunpack.GunFireMode
import com.tacz.legacy.common.domain.gunpack.GunReloadData
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import org.junit.Assert.assertEquals
import org.junit.Test

public class GunDataWeaponSpecMapperTest {

    @Test
    public fun `mapper should prefer auto mode and convert reload seconds to ticks`() {
        val gunData = sampleGunData(
            fireModes = setOf(GunFireMode.SEMI, GunFireMode.AUTO),
            emptyReloadSeconds = 2.4f
        )

        val spec = GunDataWeaponSpecMapper.toWeaponSpec(gunData, ticksPerSecond = 20)

        assertEquals(WeaponFireMode.AUTO, spec.fireMode)
        assertEquals(48, spec.reloadTicks)
        assertEquals(30, spec.magazineSize)
        assertEquals(720, spec.roundsPerMinute)
        assertEquals(55.0, spec.maxDistance, 0.0001)
    }

    @Test
    public fun `mapper should fallback to semi when auto and burst are absent`() {
        val gunData = sampleGunData(
            fireModes = setOf(GunFireMode.UNKNOWN),
            emptyReloadSeconds = -1.0f,
            tacticalReloadSeconds = 1.25f
        )

        val spec = GunDataWeaponSpecMapper.toWeaponSpec(gunData, ticksPerSecond = 20)

        assertEquals(WeaponFireMode.SEMI, spec.fireMode)
        assertEquals(25, spec.reloadTicks)
        assertEquals(55.0, spec.maxDistance, 0.0001)
    }

    @Test
    public fun `mapper should keep reload ticks at least one`() {
        val gunData = sampleGunData(
            fireModes = setOf(GunFireMode.BURST),
            emptyReloadSeconds = 0.0f,
            tacticalReloadSeconds = 0.0f
        )

        val spec = GunDataWeaponSpecMapper.toWeaponSpec(gunData, ticksPerSecond = 20)

        assertEquals(WeaponFireMode.BURST, spec.fireMode)
        assertEquals(1, spec.reloadTicks)
        assertEquals(55.0, spec.maxDistance, 0.0001)
    }

    private fun sampleGunData(
        fireModes: Set<GunFireMode>,
        emptyReloadSeconds: Float,
        tacticalReloadSeconds: Float = 2.0f
    ): GunData =
        GunData(
            sourceId = "sample:ak47_data.json",
            gunId = "ak47",
            ammoId = "tacz:762",
            ammoAmount = 30,
            extendedMagAmmoAmount = null,
            canCrawl = true,
            canSlide = true,
            boltType = GunBoltType.CLOSED_BOLT,
            roundsPerMinute = 720,
            fireModes = fireModes,
            bullet = GunBulletData(
                lifeSeconds = 10.0f,
                bulletAmount = 1,
                damage = 6.0f,
                speed = 5.5f,
                gravity = 0.02f,
                pierce = 2
            ),
            reload = GunReloadData(
                type = GunFeedType.MAGAZINE,
                infinite = false,
                emptyTimeSeconds = emptyReloadSeconds,
                tacticalTimeSeconds = tacticalReloadSeconds
            )
        )

}
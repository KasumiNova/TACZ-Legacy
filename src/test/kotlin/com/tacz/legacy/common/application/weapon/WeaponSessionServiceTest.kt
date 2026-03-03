package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunPackCompatibilityBatchAnalyzer
import com.tacz.legacy.common.application.gunpack.GunPackCompatibilitySource
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeRegistry
import com.tacz.legacy.common.application.port.HitKind
import com.tacz.legacy.common.application.port.RaycastHit
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.testing.RuntimePortsFixtures
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponSessionServiceTest {

    @Test
    public fun `open and dispatch should drive weapon behavior outputs`() {
        val runtimeRegistry = weaponRuntimeRegistryWithSingleGun("ak47")
        val fixture = RuntimePortsFixtures.create(seed = 21L)
        fixture.world.setRaycastHit(RaycastHit(kind = HitKind.ENTITY, entityId = 9))

        val service = WeaponSessionService(
            runtimeRegistry = runtimeRegistry,
            behaviorEngine = WeaponPortBehaviorEngine(fixture.world, fixture.audio, fixture.particles)
        )

        val handle = service.openSession(
            sessionId = "player:1",
            gunId = "ak47",
            ammoReserve = 90
        )
        assertNotNull(handle)
        assertTrue(service.hasSession("player:1"))
        assertEquals(1, service.sessionCount())

        val result = service.dispatch(
            sessionId = "player:1",
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0)
        )

        assertNotNull(result)
        assertTrue(result?.step?.shotFired == true)
        assertEquals(HitKind.ENTITY, result?.raycastHit?.kind)
        assertEquals(1, fixture.audio.recorded().size)
        assertEquals(1, fixture.particles.recorded().size)
        assertEquals(29, service.snapshot("player:1")?.ammoInMagazine)
    }

    @Test
    public fun `dispatch should return null for unknown session and unknown gun should not open`() {
        val runtimeRegistry = weaponRuntimeRegistryWithSingleGun("ak47")
        val fixture = RuntimePortsFixtures.create(seed = 22L)
        val service = WeaponSessionService(
            runtimeRegistry = runtimeRegistry,
            behaviorEngine = WeaponPortBehaviorEngine(fixture.world, fixture.audio, fixture.particles)
        )

        val unknownOpen = service.openSession(sessionId = "player:2", gunId = "unknown")
        assertNull(unknownOpen)
        assertEquals(0, service.sessionCount())

        val unknownDispatch = service.dispatch(
            sessionId = "player:404",
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d.ZERO,
            shotDirection = Vec3d(0.0, 0.0, 1.0)
        )
        assertNull(unknownDispatch)
        assertEquals(0, fixture.audio.recorded().size)
        assertEquals(0, fixture.particles.recorded().size)
    }

    @Test
    public fun `close and clear should remove sessions`() {
        val runtimeRegistry = weaponRuntimeRegistryWithSingleGun("smg")
        val fixture = RuntimePortsFixtures.create(seed = 23L)
        val service = WeaponSessionService(
            runtimeRegistry = runtimeRegistry,
            behaviorEngine = WeaponPortBehaviorEngine(fixture.world, fixture.audio, fixture.particles)
        )

        service.openSession(sessionId = "a", gunId = "smg")
        service.openSession(sessionId = "b", gunId = "smg")
        assertEquals(2, service.sessionCount())

        assertTrue(service.closeSession("a"))
        assertFalse(service.hasSession("a"))
        assertEquals(1, service.sessionCount())

        service.clearSessions()
        assertEquals(0, service.sessionCount())
        assertFalse(service.closeSession("b"))
    }

    @Test
    public fun `debug snapshot should expose session metadata and state`() {
        val runtimeRegistry = weaponRuntimeRegistryWithSingleGun("ak47")
        val fixture = RuntimePortsFixtures.create(seed = 24L)
        val service = WeaponSessionService(
            runtimeRegistry = runtimeRegistry,
            behaviorEngine = WeaponPortBehaviorEngine(fixture.world, fixture.audio, fixture.particles)
        )

        service.openSession(
            sessionId = "player:debug",
            gunId = "ak47",
            ammoReserve = 60,
            ammoInMagazine = 10
        )

        val debug = service.debugSnapshot("player:debug")
        assertNotNull(debug)
        assertEquals("ak47", debug?.gunId)
        assertEquals("ak47.json", debug?.sourceId)
        assertEquals(10, debug?.snapshot?.ammoInMagazine)
        assertEquals(60, debug?.snapshot?.ammoReserve)

        assertNull(service.debugSnapshot("player:missing"))
    }

    @Test
    public fun `upsertAuthoritativeSnapshot should replace local session state`() {
        val runtimeRegistry = weaponRuntimeRegistryWithSingleGun("ak47")
        val fixture = RuntimePortsFixtures.create(seed = 25L)
        val service = WeaponSessionService(
            runtimeRegistry = runtimeRegistry,
            behaviorEngine = WeaponPortBehaviorEngine(fixture.world, fixture.audio, fixture.particles)
        )

        service.openSession(
            sessionId = "player:sync",
            gunId = "ak47",
            ammoReserve = 10,
            ammoInMagazine = 30
        )

        val handle = service.upsertAuthoritativeSnapshot(
            sessionId = "player:sync",
            gunId = "ak47",
            snapshot = WeaponSnapshot(
                state = WeaponState.RELOADING,
                ammoInMagazine = 4,
                ammoReserve = 88,
                reloadTicksRemaining = 7,
                cooldownTicksRemaining = 1,
                totalShotsFired = 9
            )
        )

        assertNotNull(handle)
        val debug = service.debugSnapshot("player:sync")
        assertEquals("ak47", debug?.gunId)
        assertEquals(WeaponState.RELOADING, debug?.snapshot?.state)
        assertEquals(4, debug?.snapshot?.ammoInMagazine)
        assertEquals(88, debug?.snapshot?.ammoReserve)
        assertEquals(7, debug?.snapshot?.reloadTicksRemaining)
        assertEquals(9, debug?.snapshot?.totalShotsFired)
    }

    private fun weaponRuntimeRegistryWithSingleGun(gunId: String): WeaponRuntimeRegistry {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(
            analyzer.analyze(
                listOf(
                    GunPackCompatibilitySource("$gunId.json", validGunJson(gunId = gunId))
                )
            )
        )

        return WeaponRuntimeRegistry().also { registry ->
            registry.replaceFromGunPack(gunPackSnapshot)
        }
    }

    private fun validGunJson(gunId: String): String =
        """
        {
          "id": "$gunId",
          "ammo": "tacz:556",
          "ammo_amount": 30,
          "bolt": "closed_bolt",
          "rpm": 700,
          "fire_mode": ["auto", "semi"],
          "reload": {
            "type": "magazine",
            "infinite": false,
            "feed": {
              "empty": 2.3,
              "tactical": 2.0
            }
          },
          "bullet": {
            "life": 9.0,
            "bullet_amount": 1,
            "damage": 5.8,
            "speed": 5.4,
            "gravity": 0.02,
            "pierce": 2
          }
        }
        """.trimIndent()

}
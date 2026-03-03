package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunPackCompatibilityBatchAnalyzer
import com.tacz.legacy.common.application.gunpack.GunPackCompatibilitySource
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeRegistry
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.testing.RuntimePortsFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponAutoSessionOrchestratorTest {

    @Test
    public fun `onTick should auto open session when gun id becomes available`() {
        val fixture = RuntimePortsFixtures.create(seed = 31L)
        val service = weaponSessionServiceWithGuns(fixture, setOf("ak47"))
        val orchestrator = WeaponAutoSessionOrchestrator(service)

        orchestrator.onTick(
            WeaponTickContext(
                sessionId = "player:test",
                currentGunId = "ak47",
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            )
        )

        assertTrue(service.hasSession("player:test"))
        assertEquals(1, service.sessionCount())
        assertEquals(1, orchestrator.trackedSessionCount())
    }

    @Test
    public fun `onTick should close session when gun id disappears`() {
        val fixture = RuntimePortsFixtures.create(seed = 32L)
        val service = weaponSessionServiceWithGuns(fixture, setOf("ak47"))
        val orchestrator = WeaponAutoSessionOrchestrator(service)

        orchestrator.onTick(
            WeaponTickContext(
                sessionId = "player:test",
                currentGunId = "ak47",
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            )
        )
        assertTrue(service.hasSession("player:test"))

        orchestrator.onTick(
            WeaponTickContext(
                sessionId = "player:test",
                currentGunId = null,
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            )
        )

        assertFalse(service.hasSession("player:test"))
        assertEquals(0, service.sessionCount())
        assertEquals(0, orchestrator.trackedSessionCount())
    }

    @Test
    public fun `onTick should switch tracked gun by recreating session`() {
        val fixture = RuntimePortsFixtures.create(seed = 33L)
        val service = weaponSessionServiceWithGuns(fixture, setOf("ak47", "m4a1"))
        val orchestrator = WeaponAutoSessionOrchestrator(service)

        orchestrator.onTick(
            WeaponTickContext(
                sessionId = "player:test",
                currentGunId = "ak47",
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            )
        )
        val initialAmmo = service.snapshot("player:test")?.ammoInMagazine

        orchestrator.onTick(
            WeaponTickContext(
                sessionId = "player:test",
                currentGunId = "m4a1",
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            )
        )

        assertTrue(service.hasSession("player:test"))
        assertEquals(1, service.sessionCount())
        assertEquals(1, orchestrator.trackedSessionCount())
        assertEquals(initialAmmo, service.snapshot("player:test")?.ammoInMagazine)
    }

    @Test
    public fun `onInput should auto open session and dispatch trigger`() {
        val fixture = RuntimePortsFixtures.create(seed = 34L)
        val service = weaponSessionServiceWithGuns(fixture, setOf("ak47"))
        val orchestrator = WeaponAutoSessionOrchestrator(service)

        val result = orchestrator.onInput(
            context = WeaponTickContext(
                sessionId = "player:test",
                currentGunId = "ak47",
                muzzlePosition = Vec3d(1.0, 65.0, 2.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            ),
            input = com.tacz.legacy.common.domain.weapon.WeaponInput.TriggerPressed
        )

        assertNotNull(result)
        assertTrue(result?.step?.shotFired == true)
        assertTrue(service.hasSession("player:test"))
        assertEquals(1, fixture.audio.recorded().size)
        assertEquals(1, fixture.particles.recorded().size)
    }

    @Test
    public fun `onInput should return null and clear session when gun id missing`() {
        val fixture = RuntimePortsFixtures.create(seed = 35L)
        val service = weaponSessionServiceWithGuns(fixture, setOf("ak47"))
        val orchestrator = WeaponAutoSessionOrchestrator(service)

        orchestrator.onTick(
            WeaponTickContext(
                sessionId = "player:test",
                currentGunId = "ak47",
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            )
        )
        assertTrue(service.hasSession("player:test"))

        val result = orchestrator.onInput(
            context = WeaponTickContext(
                sessionId = "player:test",
                currentGunId = null,
                muzzlePosition = Vec3d(0.0, 64.0, 0.0),
                shotDirection = Vec3d(0.0, 0.0, 1.0)
            ),
            input = com.tacz.legacy.common.domain.weapon.WeaponInput.ReloadPressed
        )

        assertNull(result)
        assertFalse(service.hasSession("player:test"))
        assertEquals(0, orchestrator.trackedSessionCount())
    }

    private fun weaponSessionServiceWithGuns(
        fixture: com.tacz.legacy.common.application.testing.RuntimePortsFixture,
        gunIds: Set<String>
    ): WeaponSessionService {
        val analyzer = GunPackCompatibilityBatchAnalyzer()
        val sources = gunIds.sorted().map { gunId ->
            GunPackCompatibilitySource("$gunId.json", validGunJson(gunId))
        }
        val gunPackSnapshot = GunPackRuntimeRegistry().replace(analyzer.analyze(sources))

        val runtimeRegistry = WeaponRuntimeRegistry().also { registry ->
            registry.replaceFromGunPack(gunPackSnapshot)
        }

        val behaviorEngine = WeaponPortBehaviorEngine(fixture.world, fixture.audio, fixture.particles)
        return WeaponSessionService(runtimeRegistry, behaviorEngine)
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
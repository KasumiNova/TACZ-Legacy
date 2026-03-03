package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.port.HitKind
import com.tacz.legacy.common.application.port.RaycastHit
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.testing.RuntimePortsFixtures
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponSpec
import com.tacz.legacy.common.domain.weapon.WeaponStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.acos
import kotlin.math.sqrt

public class WeaponPortBehaviorEngineTest {

    @Test
    public fun `dispatch should emit sound particle and raycast when shot fired`() {
        val fixture = RuntimePortsFixtures.create(seed = 11L)
        fixture.world.setRaycastHit(
            RaycastHit(
                kind = HitKind.BLOCK,
                position = Vec3d(3.0, 64.0, 7.0)
            )
        )

        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        val result = engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0)
        )

        assertEquals(1, fixture.audio.recorded().size)
        assertEquals(1, fixture.particles.recorded().size)
        assertEquals(1, fixture.world.recordedBullets().size)
        assertNotNull(result.raycastHit)
        assertNotNull(result.bulletEntityId)
        assertEquals(HitKind.BLOCK, result.raycastHit?.kind)
        assertEquals("tacz:weapon.shoot", fixture.audio.recorded().first().soundId)
        assertEquals("tacz:muzzle_flash", fixture.particles.recorded().first().particleType)
        assertTrue(result.animationSignals.contains(WeaponAnimationSignal.FIRE))
    }

    @Test
    public fun `dispatch should stay silent when shot is blocked by cooldown`() {
        val fixture = RuntimePortsFixtures.create(seed = 12L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0)
        )

        val blocked = engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0)
        )

        assertEquals(1, fixture.audio.recorded().size)
        assertEquals(1, fixture.particles.recorded().size)
        assertEquals(1, fixture.world.recordedBullets().size)
        assertNull(blocked.raycastHit)
        assertNull(blocked.bulletEntityId)
        assertNull(blocked.emittedSound)
        assertNull(blocked.emittedParticle)
        assertTrue(blocked.animationSignals.isEmpty())
    }

    @Test
    public fun `dispatch should emit dry fire sound and signal when magazine is empty`() {
        val fixture = RuntimePortsFixtures.create(seed = 13L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 0, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        val result = engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0),
            config = WeaponBehaviorConfig(dryFireSoundId = "tacz:dry_fire")
        )

        assertEquals(1, fixture.audio.recorded().size)
        assertEquals("tacz:dry_fire", fixture.audio.recorded().first().soundId)
        assertEquals(0, fixture.particles.recorded().size)
        assertNull(result.raycastHit)
        assertTrue(result.step.dryFired)
        assertTrue(result.animationSignals.contains(WeaponAnimationSignal.DRY_FIRE))
    }

    @Test
    public fun `dispatch should emit inspect signal and optional inspect sound`() {
        val fixture = RuntimePortsFixtures.create(seed = 14L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        val result = engine.dispatch(
            machine = machine,
            input = WeaponInput.InspectPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0),
            config = WeaponBehaviorConfig(inspectSoundId = "tacz:ak47/ak47_inspect")
        )

        assertEquals(1, fixture.audio.recorded().size)
        assertEquals("tacz:ak47/ak47_inspect", fixture.audio.recorded().first().soundId)
        assertEquals(0, fixture.particles.recorded().size)
        assertNull(result.raycastHit)
        assertFalse(result.step.shotFired)
        assertTrue(result.animationSignals.contains(WeaponAnimationSignal.INSPECT))
    }

    @Test
    public fun `dispatch should prefer inspect empty sound when magazine is empty`() {
        val fixture = RuntimePortsFixtures.create(seed = 15L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 0, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        val result = engine.dispatch(
            machine = machine,
            input = WeaponInput.InspectPressed,
            muzzlePosition = Vec3d(1.0, 2.0, 3.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0),
            config = WeaponBehaviorConfig(
                inspectSoundId = "tacz:ak47/ak47_inspect",
                inspectEmptySoundId = "tacz:ak47/ak47_inspect_empty"
            )
        )

        assertEquals(1, fixture.audio.recorded().size)
        assertEquals("tacz:ak47/ak47_inspect_empty", fixture.audio.recorded().first().soundId)
        assertEquals(0, fixture.particles.recorded().size)
        assertNull(result.raycastHit)
        assertFalse(result.step.shotFired)
        assertTrue(result.animationSignals.contains(WeaponAnimationSignal.INSPECT))
    }

    @Test
    public fun `dispatch should keep exact direction when bullet inaccuracy is zero`() {
        val fixture = RuntimePortsFixtures.create(seed = 16L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(0.0, 0.0, 0.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0),
            config = WeaponBehaviorConfig(bulletInaccuracyDegrees = 0f)
        )

        val request = fixture.world.recordedBullets().first()
        assertEquals(0.0, request.direction.x, 1.0e-6)
        assertEquals(0.0, request.direction.y, 1.0e-6)
        assertEquals(1.0, request.direction.z, 1.0e-6)
        assertEquals(0.0f, request.inaccuracyDegrees, 0.0001f)
    }

    @Test
    public fun `dispatch should constrain spread direction inside configured cone`() {
        val fixture = RuntimePortsFixtures.create(seed = 17L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )
        val inaccuracy = 6.0f

        engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(0.0, 0.0, 0.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0),
            config = WeaponBehaviorConfig(bulletInaccuracyDegrees = inaccuracy)
        )

        val request = fixture.world.recordedBullets().first()
        val directionLength = sqrt(
            request.direction.x * request.direction.x +
                request.direction.y * request.direction.y +
                request.direction.z * request.direction.z
        )
        assertEquals(1.0, directionLength, 1.0e-6)

        val dot = request.direction.z.coerceIn(-1.0, 1.0)
        val angleDegrees = Math.toDegrees(acos(dot))
        assertTrue(angleDegrees <= inaccuracy + 1.0e-3)
        assertEquals(inaccuracy, request.inaccuracyDegrees, 0.0001f)
    }

    @Test
    public fun `dispatch should spawn multiple pellets when pellet count is configured`() {
        val fixture = RuntimePortsFixtures.create(seed = 18L)
        val machine = WeaponStateMachine(
            spec = WeaponSpec(magazineSize = 3, roundsPerMinute = 600, reloadTicks = 2),
            initialSnapshot = WeaponSnapshot(ammoInMagazine = 3, ammoReserve = 0)
        )
        val engine = WeaponPortBehaviorEngine(
            world = fixture.world,
            audio = fixture.audio,
            particles = fixture.particles
        )

        val result = engine.dispatch(
            machine = machine,
            input = WeaponInput.TriggerPressed,
            muzzlePosition = Vec3d(0.0, 0.0, 0.0),
            shotDirection = Vec3d(0.0, 0.0, 1.0),
            config = WeaponBehaviorConfig(
                bulletPelletCount = 6,
                bulletInaccuracyDegrees = 4.0f
            )
        )

        assertEquals(6, fixture.world.recordedBullets().size)
        assertEquals(1, fixture.audio.recorded().size)
        assertEquals(1, fixture.particles.recorded().size)
        assertNotNull(result.bulletEntityId)
    }

}
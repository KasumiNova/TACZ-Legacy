package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.port.AudioPort
import com.tacz.legacy.common.application.port.BulletCreationRequest
import com.tacz.legacy.common.application.port.DistanceDamagePairDto
import com.tacz.legacy.common.application.port.ParticlePort
import com.tacz.legacy.common.application.port.ParticleRequest
import com.tacz.legacy.common.application.port.RaycastHit
import com.tacz.legacy.common.application.port.RaycastQuery
import com.tacz.legacy.common.application.port.SoundRequest
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.port.WorldPort
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponStateMachine
import com.tacz.legacy.common.domain.weapon.WeaponStepResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

public enum class WeaponAnimationSignal {
    FIRE,
    INSPECT,
    RELOAD_START,
    RELOAD_COMPLETE,
    DRY_FIRE
}

public data class WeaponBehaviorConfig(
    val shootSoundId: String = "tacz:weapon.shoot",
    val dryFireSoundId: String? = "tacz:dry_fire",
    val inspectSoundId: String? = null,
    val inspectEmptySoundId: String? = null,
    val reloadEmptySoundId: String? = null,
    val reloadTacticalSoundId: String? = null,
    val muzzleParticleType: String = "tacz:muzzle_flash",
    val maxDistance: Double = 128.0,
    val bulletSpeed: Float = 5.0f,
    val bulletGravity: Float = 0.0f,
    val bulletFriction: Float = 0.01f,
    val bulletDamage: Float = 5.0f,
    val bulletLifeTicks: Int = 200,
    val bulletPierce: Int = 1,
    val bulletPelletCount: Int = 1,
    val bulletInaccuracyDegrees: Float = 0.0f,
    val bulletArmorIgnore: Float = 0f,
    val bulletHeadShotMultiplier: Float = 1f,
    val bulletDamageAdjust: List<DistanceDamagePairDto> = emptyList(),
    val fireSoundPitchBase: Float = 1.0f,
    val fireSoundPitchJitter: Float = 0.0f
)

public data class WeaponBehaviorResult(
    val step: WeaponStepResult,
    val bulletEntityId: Int? = null,
    val raycastHit: RaycastHit? = null,
    val emittedSound: SoundRequest? = null,
    val emittedParticle: ParticleRequest? = null,
    val animationSignals: Set<WeaponAnimationSignal> = emptySet()
)

public class WeaponPortBehaviorEngine(
    private val world: WorldPort,
    private val audio: AudioPort,
    private val particles: ParticlePort
) {

    public fun dispatch(
        machine: WeaponStateMachine,
        input: WeaponInput,
        muzzlePosition: Vec3d,
        shotDirection: Vec3d,
        config: WeaponBehaviorConfig = WeaponBehaviorConfig()
    ): WeaponBehaviorResult {
        val before = machine.snapshot()
        val step = machine.dispatch(input)
        val animationSignals = linkedSetOf<WeaponAnimationSignal>()

        if (input == WeaponInput.InspectPressed) {
            animationSignals += WeaponAnimationSignal.INSPECT

            val inspectSoundId = if (before.ammoInMagazine <= 0) {
                config.inspectEmptySoundId ?: config.inspectSoundId
            } else {
                config.inspectSoundId
            }

            val inspectRequest = inspectSoundId
                ?.trim()
                ?.ifBlank { null }
                ?.let { soundId -> SoundRequest(soundId = soundId, position = muzzlePosition) }
            if (inspectRequest != null) {
                audio.play(inspectRequest)
            }

            return WeaponBehaviorResult(
                step = step,
                emittedSound = inspectRequest,
                animationSignals = animationSignals
            )
        }

        if (step.reloadStarted) {
            animationSignals += WeaponAnimationSignal.RELOAD_START

            val reloadSoundId = if (before.ammoInMagazine <= 0) {
                config.reloadEmptySoundId
            } else {
                config.reloadTacticalSoundId
            }
            reloadSoundId
                ?.trim()
                ?.ifBlank { null }
                ?.let { soundId -> SoundRequest(soundId = soundId, position = muzzlePosition) }
                ?.also(audio::play)
        }
        if (step.reloadCompleted) {
            animationSignals += WeaponAnimationSignal.RELOAD_COMPLETE
        }

        if (step.dryFired) {
            animationSignals += WeaponAnimationSignal.DRY_FIRE
            val dryFireRequest = config.dryFireSoundId
                ?.trim()
                ?.ifBlank { null }
                ?.let { soundId -> SoundRequest(soundId = soundId, position = muzzlePosition) }

            if (dryFireRequest != null) {
                audio.play(dryFireRequest)
            }

            return WeaponBehaviorResult(
                step = step,
                emittedSound = dryFireRequest,
                animationSignals = animationSignals
            )
        }

        if (!step.shotFired) {
            return WeaponBehaviorResult(
                step = step,
                animationSignals = animationSignals
            )
        }

        animationSignals += WeaponAnimationSignal.FIRE

        val fireSoundPitch = if (config.fireSoundPitchJitter > 0.0f) {
            val jitter = (Random.nextFloat() * 2.0f - 1.0f) * config.fireSoundPitchJitter
            (config.fireSoundPitchBase + jitter).coerceAtLeast(0.01f)
        } else {
            config.fireSoundPitchBase
        }

        val soundRequest = SoundRequest(
            soundId = config.shootSoundId,
            position = muzzlePosition,
            pitch = fireSoundPitch
        )
        audio.play(soundRequest)

        val particleRequest = ParticleRequest(
            particleType = config.muzzleParticleType,
            position = muzzlePosition
        )
        particles.spawn(particleRequest)

        val normalizedDirection = normalizeDirection(shotDirection)
        val pelletCount = config.bulletPelletCount.coerceAtLeast(1)

        var firstBulletEntityId: Int? = null
        var bestHit: RaycastHit? = null
        var bestHitDistanceSq: Double = Double.POSITIVE_INFINITY

        repeat(pelletCount) {
            val spreadDirection = applyInaccuracy(
                direction = normalizedDirection,
                inaccuracyDegrees = config.bulletInaccuracyDegrees
            )

            val bulletEntityId = world.createBullet(
                BulletCreationRequest(
                    origin = muzzlePosition,
                    direction = spreadDirection,
                    speed = config.bulletSpeed,
                    gravity = config.bulletGravity,
                    friction = config.bulletFriction,
                    damage = config.bulletDamage,
                    maxLifetimeTicks = config.bulletLifeTicks,
                    pierce = config.bulletPierce,
                    inaccuracyDegrees = config.bulletInaccuracyDegrees,
                    armorIgnore = config.bulletArmorIgnore,
                    headShotMultiplier = config.bulletHeadShotMultiplier,
                    damageAdjust = config.bulletDamageAdjust
                )
            )
            if (firstBulletEntityId == null) {
                firstBulletEntityId = bulletEntityId
            }

            val hit = world.raycast(
                RaycastQuery(
                    origin = muzzlePosition,
                    direction = spreadDirection,
                    maxDistance = config.maxDistance
                )
            )
            val hitDistanceSq = hit.position?.let { pos ->
                val dx = pos.x - muzzlePosition.x
                val dy = pos.y - muzzlePosition.y
                val dz = pos.z - muzzlePosition.z
                dx * dx + dy * dy + dz * dz
            } ?: Double.POSITIVE_INFINITY

            if (bestHit == null || hitDistanceSq < bestHitDistanceSq) {
                bestHit = hit
                bestHitDistanceSq = hitDistanceSq
            }
        }

        return WeaponBehaviorResult(
            step = step,
            bulletEntityId = firstBulletEntityId,
            raycastHit = bestHit,
            emittedSound = soundRequest,
            emittedParticle = particleRequest,
            animationSignals = animationSignals
        )
    }

    private fun normalizeDirection(direction: Vec3d): Vec3d {
        val lengthSq = direction.x * direction.x + direction.y * direction.y + direction.z * direction.z
        if (lengthSq <= 1.0e-8) {
            return Vec3d(0.0, 0.0, 1.0)
        }
        val invLength = 1.0 / sqrt(lengthSq)
        return Vec3d(
            x = direction.x * invLength,
            y = direction.y * invLength,
            z = direction.z * invLength
        )
    }

    private fun applyInaccuracy(direction: Vec3d, inaccuracyDegrees: Float): Vec3d {
        val maxAngleRadians = Math.toRadians(inaccuracyDegrees.toDouble().coerceAtLeast(0.0))
        if (maxAngleRadians <= 1.0e-8) {
            return direction
        }

        val forward = normalizeDirection(direction)
        val auxUp = if (abs(forward.y) < 0.999) {
            Vec3d(0.0, 1.0, 0.0)
        } else {
            Vec3d(1.0, 0.0, 0.0)
        }
        val right = normalizeDirection(cross(auxUp, forward))
        val up = normalizeDirection(cross(forward, right))

        val theta = Random.nextDouble(0.0, PI * 2.0)
        val cosMax = cos(maxAngleRadians)
        val cosPhi = Random.nextDouble(cosMax, 1.0)
        val sinPhi = sqrt((1.0 - cosPhi * cosPhi).coerceAtLeast(0.0))

        val localX = cos(theta) * sinPhi
        val localY = sin(theta) * sinPhi
        val localZ = cosPhi

        val perturbed = Vec3d(
            x = right.x * localX + up.x * localY + forward.x * localZ,
            y = right.y * localX + up.y * localY + forward.y * localZ,
            z = right.z * localX + up.z * localY + forward.z * localZ
        )
        return normalizeDirection(perturbed)
    }

    private fun cross(a: Vec3d, b: Vec3d): Vec3d = Vec3d(
        x = a.y * b.z - a.z * b.y,
        y = a.z * b.x - a.x * b.z,
        z = a.x * b.y - a.y * b.x
    )

}
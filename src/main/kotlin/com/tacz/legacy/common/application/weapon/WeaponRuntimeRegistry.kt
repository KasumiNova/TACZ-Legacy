package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponFireMode
import com.tacz.legacy.common.domain.weapon.WeaponSpec
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.domain.weapon.WeaponStateMachine

public data class WeaponDefinition(
    val sourceId: String,
    val gunId: String,
    val spec: com.tacz.legacy.common.domain.weapon.WeaponSpec,
    val ballistics: WeaponBallistics,
    val scriptParams: Map<String, Float> = emptyMap()
)

public data class WeaponBallistics(
    val speed: Float,
    val gravity: Float,
    val friction: Float = 0.01f,
    val damage: Float,
    val lifetimeTicks: Int,
    val pierce: Int,
    val pelletCount: Int,
    val inaccuracy: WeaponInaccuracyProfile = WeaponInaccuracyProfile(),
    val armorIgnore: Float = 0f,
    val headShotMultiplier: Float = 1f,
    val damageAdjust: List<WeaponDistanceDamagePair> = emptyList(),
    val knockback: Float = 0f,
    val igniteEntity: Boolean = false,
    val igniteEntityTime: Int = 2,
    val igniteBlock: Boolean = false,
    val explosion: WeaponExplosionData? = null
)

public data class WeaponDistanceDamagePair(
    val distance: Float,
    val damage: Float
)

public data class WeaponExplosionData(
    val radius: Float = 0f,
    val damage: Float = 0f,
    val knockback: Boolean = false,
    val destroyBlock: Boolean = false,
    val delaySeconds: Float = 30f
)

public data class WeaponInaccuracyProfile(
    val stand: Float = 0.0f,
    val move: Float = 0.0f,
    val sneak: Float = 0.0f,
    val lie: Float = 0.0f,
    val aim: Float = 0.0f
)

public data class WeaponRuntimeSnapshot(
    val loadedAtEpochMillis: Long,
    val totalDefinitions: Int,
    val definitionsByGunId: Map<String, WeaponDefinition>,
    val failedGunIds: Set<String>
) {

    public fun findDefinition(gunId: String): WeaponDefinition? = definitionsByGunId[gunId]

    public companion object {
        public fun empty(atEpochMillis: Long = System.currentTimeMillis()): WeaponRuntimeSnapshot =
            WeaponRuntimeSnapshot(
                loadedAtEpochMillis = atEpochMillis,
                totalDefinitions = 0,
                definitionsByGunId = emptyMap(),
                failedGunIds = emptySet()
            )
    }

}

public data class WeaponSession(
    val sourceId: String,
    val gunId: String,
    val machine: WeaponStateMachine,
    val defaultBehaviorConfig: WeaponBehaviorConfig
)

public class WeaponRuntimeRegistry(
    private val mapper: GunDataWeaponSpecMapper = GunDataWeaponSpecMapper
) {

    @Volatile
    private var latestSnapshot: WeaponRuntimeSnapshot = WeaponRuntimeSnapshot.empty()

    @Synchronized
    public fun replaceFromGunPack(snapshot: GunPackRuntimeSnapshot): WeaponRuntimeSnapshot {
        val definitionsByGunId = linkedMapOf<String, WeaponDefinition>()
        val failedGunIds = linkedSetOf<String>()

        snapshot.sourceIdByGunId
            .toSortedMap()
            .forEach { (gunId, sourceId) ->
                val data = snapshot.findBySourceId(sourceId)
                if (data == null) {
                    failedGunIds += gunId
                    return@forEach
                }

                runCatching {
                    mapper.toWeaponSpec(data)
                }.onSuccess { spec ->
                    val ballistics = WeaponBallistics(
                        speed = (data.bullet.speed / TICKS_PER_SECOND).coerceAtLeast(MIN_PROJECTILE_SPEED_PER_TICK),
                        gravity = data.bullet.gravity.coerceAtLeast(0.0f),
                        friction = data.bullet.friction.coerceIn(0.0f, 1.0f),
                        damage = data.bullet.damage.coerceAtLeast(0.0f),
                        lifetimeTicks = (data.bullet.lifeSeconds.coerceAtLeast(0.05f) * TICKS_PER_SECOND)
                            .toInt()
                            .coerceAtLeast(1),
                        pierce = data.bullet.pierce.coerceAtLeast(1),
                        pelletCount = data.bullet.bulletAmount.coerceAtLeast(1),
                        inaccuracy = WeaponInaccuracyProfile(
                            stand = data.inaccuracy.stand.coerceAtLeast(0.0f),
                            move = data.inaccuracy.move.coerceAtLeast(0.0f),
                            sneak = data.inaccuracy.sneak.coerceAtLeast(0.0f),
                            lie = data.inaccuracy.lie.coerceAtLeast(0.0f),
                            aim = data.inaccuracy.aim.coerceAtLeast(0.0f)
                        ),
                        armorIgnore = data.bullet.extraDamage.armorIgnore.coerceIn(0f, 1f),
                        headShotMultiplier = data.bullet.extraDamage.headShotMultiplier.coerceAtLeast(0f),
                        damageAdjust = data.bullet.extraDamage.damageAdjust.map {
                            WeaponDistanceDamagePair(distance = it.distance, damage = it.damage)
                        },
                        knockback = data.bullet.knockback.coerceAtLeast(0f),
                        igniteEntity = data.bullet.ignite.entity,
                        igniteEntityTime = data.bullet.igniteEntityTime.coerceAtLeast(0),
                        igniteBlock = data.bullet.ignite.block,
                        explosion = data.bullet.explosion?.takeIf { it.explode }?.let {
                            WeaponExplosionData(
                                radius = it.radius.coerceAtLeast(0f),
                                damage = it.damage.coerceAtLeast(0f),
                                knockback = it.knockback,
                                destroyBlock = it.destroyBlock,
                                delaySeconds = it.delaySeconds.coerceAtLeast(0f)
                            )
                        }
                    )

                    val adjustKey = spec.fireMode.name
                    val adjust = data.fireModeAdjust[adjustKey]

                    val adjustedBallistics = if (adjust != null) {
                        ballistics.copy(
                            damage = (ballistics.damage + adjust.damageAmount).coerceAtLeast(0f),
                            speed = (ballistics.speed + adjust.speed / TICKS_PER_SECOND).coerceAtLeast(MIN_PROJECTILE_SPEED_PER_TICK),
                            knockback = (ballistics.knockback + adjust.knockback).coerceAtLeast(0f),
                            armorIgnore = (ballistics.armorIgnore + adjust.armorIgnore).coerceIn(0f, 1f),
                            headShotMultiplier = (ballistics.headShotMultiplier + adjust.headShotMultiplier).coerceAtLeast(0f),
                            inaccuracy = ballistics.inaccuracy.copy(
                                aim = (ballistics.inaccuracy.aim + adjust.aimInaccuracy).coerceAtLeast(0f),
                                stand = (ballistics.inaccuracy.stand + adjust.otherInaccuracy).coerceAtLeast(0f),
                                move = (ballistics.inaccuracy.move + adjust.otherInaccuracy).coerceAtLeast(0f),
                                sneak = (ballistics.inaccuracy.sneak + adjust.otherInaccuracy).coerceAtLeast(0f),
                                lie = (ballistics.inaccuracy.lie + adjust.otherInaccuracy).coerceAtLeast(0f)
                            )
                        )
                    } else {
                        ballistics
                    }

                    val adjustedSpec = if (adjust != null && adjust.roundsPerMinute != 0) {
                        spec.copy(roundsPerMinute = (spec.roundsPerMinute + adjust.roundsPerMinute).coerceAtLeast(1))
                    } else {
                        spec
                    }

                    definitionsByGunId[gunId] = WeaponDefinition(
                        sourceId = sourceId,
                        gunId = gunId,
                        spec = adjustedSpec,
                            ballistics = adjustedBallistics,
                            scriptParams = data.scriptParams
                    )
                }.onFailure {
                    failedGunIds += gunId
                }
            }

        latestSnapshot = WeaponRuntimeSnapshot(
            loadedAtEpochMillis = snapshot.loadedAtEpochMillis,
            totalDefinitions = definitionsByGunId.size,
            definitionsByGunId = definitionsByGunId.toMap(),
            failedGunIds = failedGunIds
        )
        return latestSnapshot
    }

    @Synchronized
    public fun clear(): WeaponRuntimeSnapshot {
        latestSnapshot = WeaponRuntimeSnapshot.empty()
        return latestSnapshot
    }

    public fun snapshot(): WeaponRuntimeSnapshot = latestSnapshot

    public fun createSession(
        gunId: String,
        ammoReserve: Int = 0,
        ammoInMagazine: Int? = null,
        allowFallbackDefinition: Boolean = false
    ): WeaponSession? {
        val definition = resolveDefinition(
            gunId = gunId,
            allowFallbackDefinition = allowFallbackDefinition
        ) ?: return null
        val magazine = (ammoInMagazine ?: definition.spec.magazineSize)
            .coerceIn(0, definition.spec.magazineSize)
        val reserve = ammoReserve.coerceAtLeast(0)

        return WeaponSession(
            sourceId = definition.sourceId,
            gunId = definition.gunId,
            machine = WeaponStateMachine(
                spec = definition.spec,
                initialSnapshot = WeaponSnapshot(
                    ammoInMagazine = magazine,
                    ammoReserve = reserve
                )
            ),
            defaultBehaviorConfig = WeaponBehaviorConfig(
                maxDistance = definition.spec.maxDistance,
                bulletSpeed = definition.ballistics.speed,
                bulletGravity = definition.ballistics.gravity,
                bulletFriction = definition.ballistics.friction,
                bulletDamage = definition.ballistics.damage,
                bulletLifeTicks = definition.ballistics.lifetimeTicks,
                bulletPierce = definition.ballistics.pierce,
                bulletPelletCount = definition.ballistics.pelletCount,
                bulletInaccuracyDegrees = definition.ballistics.inaccuracy.stand
            )
        )
    }

    public fun createSessionFromSnapshot(
        gunId: String,
        authoritativeSnapshot: WeaponSnapshot,
        allowFallbackDefinition: Boolean = false
    ): WeaponSession? {
        val definition = resolveDefinition(
            gunId = gunId,
            allowFallbackDefinition = allowFallbackDefinition
        ) ?: return null
        val normalizedSnapshot = normalizeAuthoritativeSnapshot(definition, authoritativeSnapshot)

        return WeaponSession(
            sourceId = definition.sourceId,
            gunId = definition.gunId,
            machine = WeaponStateMachine(
                spec = definition.spec,
                initialSnapshot = normalizedSnapshot
            ),
            defaultBehaviorConfig = WeaponBehaviorConfig(
                maxDistance = definition.spec.maxDistance,
                bulletSpeed = definition.ballistics.speed,
                bulletGravity = definition.ballistics.gravity,
                bulletFriction = definition.ballistics.friction,
                bulletDamage = definition.ballistics.damage,
                bulletLifeTicks = definition.ballistics.lifetimeTicks,
                bulletPierce = definition.ballistics.pierce,
                bulletPelletCount = definition.ballistics.pelletCount,
                bulletInaccuracyDegrees = definition.ballistics.inaccuracy.stand
            )
        )
    }

    private fun normalizeAuthoritativeSnapshot(
        definition: WeaponDefinition,
        snapshot: WeaponSnapshot
    ): WeaponSnapshot {
        val clamped = snapshot.copy(
            ammoInMagazine = snapshot.ammoInMagazine.coerceIn(0, definition.spec.magazineSize),
            ammoReserve = snapshot.ammoReserve.coerceAtLeast(0)
        )

        if (clamped.state == WeaponState.RELOADING && clamped.reloadTicksRemaining <= 0) {
            return clamped.copy(reloadTicksRemaining = definition.spec.reloadTicks.coerceAtLeast(1))
        }

        return clamped
    }

    private fun resolveDefinition(
        gunId: String,
        allowFallbackDefinition: Boolean
    ): WeaponDefinition? {
        val normalizedGunId = gunId.trim().lowercase().ifBlank { return null }
        val explicit = latestSnapshot.findDefinition(normalizedGunId)
        if (explicit != null) {
            return explicit
        }
        if (!allowFallbackDefinition) {
            return null
        }
        return buildFallbackDefinition(normalizedGunId)
    }

    private fun buildFallbackDefinition(gunId: String): WeaponDefinition {
        return WeaponDefinition(
            sourceId = "$FALLBACK_SOURCE_ID_PREFIX$gunId",
            gunId = gunId,
            spec = WeaponSpec(
                magazineSize = FALLBACK_MAGAZINE_SIZE,
                roundsPerMinute = FALLBACK_RPM,
                reloadTicks = FALLBACK_RELOAD_TICKS,
                fireMode = WeaponFireMode.AUTO,
                maxDistance = FALLBACK_MAX_DISTANCE
            ),
            ballistics = WeaponBallistics(
                speed = FALLBACK_BULLET_SPEED,
                gravity = FALLBACK_BULLET_GRAVITY,
                friction = FALLBACK_BULLET_FRICTION,
                damage = FALLBACK_BULLET_DAMAGE,
                lifetimeTicks = FALLBACK_BULLET_LIFETIME_TICKS,
                pierce = FALLBACK_BULLET_PIERCE,
                pelletCount = 1,
                inaccuracy = WeaponInaccuracyProfile(
                    stand = FALLBACK_BULLET_INACCURACY,
                    move = FALLBACK_BULLET_INACCURACY,
                    sneak = FALLBACK_BULLET_INACCURACY,
                    lie = FALLBACK_BULLET_INACCURACY,
                    aim = FALLBACK_BULLET_INACCURACY
                )
            )
        )
    }

}

public object WeaponRuntime {

    private val registry: WeaponRuntimeRegistry = WeaponRuntimeRegistry()

    public fun registry(): WeaponRuntimeRegistry = registry

}

private const val TICKS_PER_SECOND: Float = 20.0f
private const val MIN_PROJECTILE_SPEED_PER_TICK: Float = 0.01f
private const val FALLBACK_SOURCE_ID_PREFIX: String = "runtime:fallback/"
private const val FALLBACK_MAGAZINE_SIZE: Int = 30
private const val FALLBACK_RPM: Int = 600
private const val FALLBACK_RELOAD_TICKS: Int = 40
private const val FALLBACK_MAX_DISTANCE: Double = 128.0
private const val FALLBACK_BULLET_SPEED: Float = 5.0f
private const val FALLBACK_BULLET_GRAVITY: Float = 0.0f
private const val FALLBACK_BULLET_FRICTION: Float = 0.01f
private const val FALLBACK_BULLET_DAMAGE: Float = 5.0f
private const val FALLBACK_BULLET_LIFETIME_TICKS: Int = 200
private const val FALLBACK_BULLET_PIERCE: Int = 1
private const val FALLBACK_BULLET_INACCURACY: Float = 0.0f
package com.tacz.legacy.common.infrastructure.mc.entity

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.DamageSource
import net.minecraft.util.EntityDamageSourceIndirect
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

public class LegacyBulletEntity : EntityThrowable {

    private var configuredDamage: Float = DEFAULT_DAMAGE
    private var configuredGravity: Float = DEFAULT_GRAVITY
    private var configuredFriction: Float = DEFAULT_FRICTION
    private var remainingPierce: Int = DEFAULT_PIERCE
    private var maxLifetimeTicks: Int = DEFAULT_LIFETIME_TICKS
    private var ageTicks: Int = 0

    private var armorIgnore: Float = 0f
    private var headShotMultiplier: Float = 1f
    private var damageAdjust: List<DamagePair> = emptyList()
    private var startPos: Vec3d = Vec3d.ZERO

    public constructor(worldIn: World) : super(worldIn) {
        setSize(BULLET_SIZE, BULLET_SIZE)
        startPos = Vec3d(posX, posY, posZ)
    }

    public constructor(worldIn: World, throwerIn: EntityLivingBase) : super(worldIn, throwerIn) {
        setSize(BULLET_SIZE, BULLET_SIZE)
        startPos = Vec3d(posX, posY, posZ)
    }

    public constructor(worldIn: World, x: Double, y: Double, z: Double) : super(worldIn, x, y, z) {
        setSize(BULLET_SIZE, BULLET_SIZE)
        startPos = Vec3d(x, y, z)
    }

    public fun configure(
        damage: Float,
        gravity: Float,
        friction: Float,
        pierce: Int,
        lifetimeTicks: Int,
        armorIgnore: Float = 0f,
        headShotMultiplier: Float = 1f,
        damageAdjust: List<DamagePair> = emptyList()
    ) {
        configuredDamage = damage.coerceAtLeast(0.0f)
        configuredGravity = gravity.coerceAtLeast(0.0f)
        configuredFriction = friction.coerceIn(0.0f, 1.0f)
        remainingPierce = pierce.coerceAtLeast(1)
        maxLifetimeTicks = lifetimeTicks.coerceAtLeast(1)
        this.armorIgnore = armorIgnore.coerceIn(0f, 1f)
        this.headShotMultiplier = headShotMultiplier.coerceAtLeast(0f)
        this.damageAdjust = damageAdjust
        this.startPos = Vec3d(posX, posY, posZ)
    }

    public override fun onUpdate() {
        val startX = posX
        val startY = posY
        val startZ = posZ
        val pierceBeforeTick = remainingPierce

        super.onUpdate()

        if (!world.isRemote && !isDead && !inGround && remainingPierce == pierceBeforeTick) {
            performSupplementalSweepHit(startX, startY, startZ)
        }

        applyFrictionCompensation()

        if (world.isRemote) {
            return
        }

        ageTicks += 1
        if (ageTicks >= maxLifetimeTicks || inGround) {
            setDead()
        }
    }

    private fun performSupplementalSweepHit(startX: Double, startY: Double, startZ: Double) {
        val start = Vec3d(startX, startY, startZ)
        val end = Vec3d(posX, posY, posZ)
        if (start.squareDistanceTo(end) <= MIN_SWEEP_DISTANCE_SQ) {
            return
        }

        val pathAabb = AxisAlignedBB(
            startX.coerceAtMost(posX),
            startY.coerceAtMost(posY),
            startZ.coerceAtMost(posZ),
            startX.coerceAtLeast(posX),
            startY.coerceAtLeast(posY),
            startZ.coerceAtLeast(posZ)
        ).grow(SUPPLEMENTAL_SWEEP_EXPAND)

        var closestDistanceSq = Double.POSITIVE_INFINITY
        var bestHit: RayTraceResult? = null

        val candidates = world.getEntitiesWithinAABBExcludingEntity(this, pathAabb)
        for (candidate in candidates) {
            if (!canHitEntity(candidate)) {
                continue
            }

            val grownBox = candidate.entityBoundingBox.grow(candidate.collisionBorderSize.toDouble())
            val intercept = grownBox.calculateIntercept(start, end) ?: continue
            val hitVec = intercept.hitVec ?: continue
            val distanceSq = start.squareDistanceTo(hitVec)
            if (distanceSq < closestDistanceSq) {
                closestDistanceSq = distanceSq
                bestHit = RayTraceResult(candidate, hitVec)
            }
        }

        if (bestHit != null) {
            onImpact(bestHit)
        }
    }

    override fun getGravityVelocity(): Float = configuredGravity

    override fun onImpact(result: RayTraceResult) {
        if (world.isRemote) {
            return
        }

        when (result.typeOfHit) {
            RayTraceResult.Type.ENTITY -> {
                val target = result.entityHit
                if (!canHitEntity(target)) {
                    return
                }

                val hitVec = result.hitVec ?: Vec3d(target.posX, target.posY, target.posZ)
                var damage = getDamageForDistance(hitVec)

                val headshot = isHeadshot(target, hitVec)
                if (headshot) {
                    damage *= headShotMultiplier
                }

                val attacked = applyDamageWithArmorIgnore(target, damage)
                if (attacked) {
                    remainingPierce -= 1
                    if (remainingPierce <= 0) {
                        setDead()
                    }
                } else {
                    setDead()
                }
            }

            RayTraceResult.Type.BLOCK -> {
                setDead()
            }

            else -> Unit
        }
    }

    private fun getDamageForDistance(hitVec: Vec3d): Float {
        if (damageAdjust.isEmpty()) {
            return configuredDamage
        }

        val playerDistance = hitVec.distanceTo(startPos)
        for (pair in damageAdjust) {
            if (playerDistance < pair.distance) {
                return pair.damage.coerceAtLeast(0f)
            }
        }

        return 0f
    }

    private fun isHeadshot(target: Entity, hitVec: Vec3d): Boolean {
        if (headShotMultiplier <= 1f) {
            return false
        }

        val relativeY = hitVec.y - target.posY
        val eyeHeight = target.eyeHeight.toDouble()
        return relativeY > (eyeHeight - HEADSHOT_Y_TOLERANCE) && relativeY < (eyeHeight + HEADSHOT_Y_TOLERANCE)
    }

    private fun applyDamageWithArmorIgnore(target: Entity, totalDamage: Float): Boolean {
        if (armorIgnore <= 0f || target !is EntityLivingBase) {
            val damageSource = thrower?.let { owner ->
                DamageSource.causeThrownDamage(this, owner)
            } ?: DamageSource.GENERIC
            return target.attackEntityFrom(damageSource, totalDamage)
        }

        val normalDamagePercent = 1f - armorIgnore
        val armorPierceDamagePercent = armorIgnore

        val normalSource = thrower?.let { owner ->
            DamageSource.causeThrownDamage(this, owner)
        } ?: DamageSource.GENERIC

        val armorPierceSource = createArmorPierceSource()

        var hit = false

        if (normalDamagePercent > 0f) {
            hit = target.attackEntityFrom(normalSource, totalDamage * normalDamagePercent)
        }

        target.hurtResistantTime = 0

        if (armorPierceDamagePercent > 0f) {
            hit = target.attackEntityFrom(armorPierceSource, totalDamage * armorPierceDamagePercent) || hit
        }

        return hit
    }

    private fun createArmorPierceSource(): DamageSource {
        val source = if (thrower != null) {
            EntityDamageSourceIndirect(ARMOR_PIERCE_DAMAGE_TYPE, this, thrower)
        } else {
            DamageSource(ARMOR_PIERCE_DAMAGE_TYPE)
        }
        source.setDamageBypassesArmor()
        return source
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        super.writeEntityToNBT(compound)
        compound.setFloat(TAG_DAMAGE, configuredDamage)
        compound.setFloat(TAG_GRAVITY, configuredGravity)
        compound.setFloat(TAG_FRICTION, configuredFriction)
        compound.setInteger(TAG_REMAINING_PIERCE, remainingPierce)
        compound.setInteger(TAG_MAX_LIFETIME_TICKS, maxLifetimeTicks)
        compound.setInteger(TAG_AGE_TICKS, ageTicks)
        compound.setFloat(TAG_ARMOR_IGNORE, armorIgnore)
        compound.setFloat(TAG_HEADSHOT_MULTIPLIER, headShotMultiplier)
        compound.setDouble(TAG_START_X, startPos.x)
        compound.setDouble(TAG_START_Y, startPos.y)
        compound.setDouble(TAG_START_Z, startPos.z)

        if (damageAdjust.isNotEmpty()) {
            val list = NBTTagList()
            for (pair in damageAdjust) {
                val entry = NBTTagCompound()
                entry.setFloat(TAG_PAIR_DISTANCE, pair.distance)
                entry.setFloat(TAG_PAIR_DAMAGE, pair.damage)
                list.appendTag(entry)
            }
            compound.setTag(TAG_DAMAGE_ADJUST, list)
        }
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        super.readEntityFromNBT(compound)
        configuredDamage = compound.getFloat(TAG_DAMAGE).coerceAtLeast(0.0f)
        configuredGravity = compound.getFloat(TAG_GRAVITY).coerceAtLeast(0.0f)
        configuredFriction = if (compound.hasKey(TAG_FRICTION)) {
            compound.getFloat(TAG_FRICTION).coerceIn(0.0f, 1.0f)
        } else {
            DEFAULT_FRICTION
        }
        remainingPierce = compound.getInteger(TAG_REMAINING_PIERCE).coerceAtLeast(1)
        maxLifetimeTicks = compound.getInteger(TAG_MAX_LIFETIME_TICKS).coerceAtLeast(1)
        ageTicks = compound.getInteger(TAG_AGE_TICKS).coerceAtLeast(0)
        armorIgnore = compound.getFloat(TAG_ARMOR_IGNORE).coerceIn(0f, 1f)
        headShotMultiplier = if (compound.hasKey(TAG_HEADSHOT_MULTIPLIER)) {
            compound.getFloat(TAG_HEADSHOT_MULTIPLIER).coerceAtLeast(0f)
        } else {
            1f
        }
        startPos = Vec3d(
            compound.getDouble(TAG_START_X),
            compound.getDouble(TAG_START_Y),
            compound.getDouble(TAG_START_Z)
        )

        if (compound.hasKey(TAG_DAMAGE_ADJUST)) {
            val list = compound.getTagList(TAG_DAMAGE_ADJUST, 10) // 10 = NBT.TAG_COMPOUND
            val pairs = mutableListOf<DamagePair>()
            for (i in 0 until list.tagCount()) {
                val entry = list.getCompoundTagAt(i)
                pairs += DamagePair(
                    distance = entry.getFloat(TAG_PAIR_DISTANCE).coerceAtLeast(0f),
                    damage = entry.getFloat(TAG_PAIR_DAMAGE).coerceAtLeast(0f)
                )
            }
            damageAdjust = pairs
        }
    }

    private fun applyFrictionCompensation() {
        if (inGround || isDead) {
            return
        }

        val inWaterNow = isInWater
        val targetVelocityScale = if (inWaterNow) {
            TACZ_WATER_VELOCITY_SCALE
        } else {
            (1.0f - configuredFriction).coerceIn(0.0f, 1.0f)
        }
        val vanillaVelocityScale = if (inWaterNow) VANILLA_WATER_FRICTION else VANILLA_AIR_FRICTION
        if (vanillaVelocityScale <= 1.0e-6f) {
            return
        }

        val compensationScale = (targetVelocityScale / vanillaVelocityScale).coerceAtLeast(0.0f).toDouble()
        motionX *= compensationScale
        motionY *= compensationScale
        motionZ *= compensationScale

        if (configuredGravity <= 0.0f) {
            return
        }

        val desiredGravityScale = if (inWaterNow) TACZ_WATER_GRAVITY_SCALE else 1.0f
        motionY += configuredGravity.toDouble() * (compensationScale - desiredGravityScale.toDouble())
    }

    private fun canHitEntity(target: Entity?): Boolean {
        if (target == null || target.isDead) {
            return false
        }

        if (target == thrower) {
            return false
        }

        return target.canBeCollidedWith()
    }

    public data class DamagePair(
        val distance: Float,
        val damage: Float
    )

    private companion object {
        private const val BULLET_SIZE: Float = 0.125f
        private const val DEFAULT_DAMAGE: Float = 5.0f
        private const val DEFAULT_GRAVITY: Float = 0.0f
        private const val DEFAULT_FRICTION: Float = 0.01f
        private const val DEFAULT_PIERCE: Int = 1
        private const val DEFAULT_LIFETIME_TICKS: Int = 200
        private const val HEADSHOT_Y_TOLERANCE: Double = 0.25
        private const val ARMOR_PIERCE_DAMAGE_TYPE: String = "tacz.bullet_ignore_armor"
        private const val VANILLA_AIR_FRICTION: Float = 0.99f
        private const val VANILLA_WATER_FRICTION: Float = 0.8f
        private const val TACZ_WATER_FRICTION: Float = 0.4f
        private const val TACZ_WATER_VELOCITY_SCALE: Float = 1.0f - TACZ_WATER_FRICTION
        private const val TACZ_WATER_GRAVITY_SCALE: Float = 0.6f
        private const val SUPPLEMENTAL_SWEEP_EXPAND: Double = 0.3
        private const val MIN_SWEEP_DISTANCE_SQ: Double = 1.0e-8
        private const val TAG_DAMAGE: String = "Damage"
        private const val TAG_GRAVITY: String = "Gravity"
        private const val TAG_FRICTION: String = "Friction"
        private const val TAG_REMAINING_PIERCE: String = "RemainingPierce"
        private const val TAG_MAX_LIFETIME_TICKS: String = "MaxLifetimeTicks"
        private const val TAG_AGE_TICKS: String = "AgeTicks"
        private const val TAG_ARMOR_IGNORE: String = "ArmorIgnore"
        private const val TAG_HEADSHOT_MULTIPLIER: String = "HeadShotMultiplier"
        private const val TAG_START_X: String = "StartX"
        private const val TAG_START_Y: String = "StartY"
        private const val TAG_START_Z: String = "StartZ"
        private const val TAG_DAMAGE_ADJUST: String = "DamageAdjust"
        private const val TAG_PAIR_DISTANCE: String = "Dist"
        private const val TAG_PAIR_DAMAGE: String = "Dmg"
    }

}
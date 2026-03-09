package com.tacz.legacy.common.entity

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.resource.BulletCombatData
import com.tacz.legacy.common.resource.DistanceDamagePoint
import com.tacz.legacy.common.config.HeadShotAabbConfigRead
import com.tacz.legacy.common.config.LegacyConfigManager
import io.netty.buffer.ByteBuf
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityMinecartEmpty
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.init.Blocks
import net.minecraft.util.DamageSource
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.fml.common.registry.EntityEntry
import net.minecraftforge.fml.common.registry.EntityEntryBuilder
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.registries.IForgeRegistry
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.sqrt

internal object LegacyEntities {
    internal val BULLET: EntityEntry = EntityEntryBuilder.create<EntityKineticBullet>()
        .entity(EntityKineticBullet::class.java)
        .id(ResourceLocation(TACZLegacy.MOD_ID, "bullet"), 0)
        .name("bullet")
        .tracker(64, 1, true)
        .build()

    internal val TARGET_MINECART: EntityEntry = EntityEntryBuilder.create<TargetMinecartEntity>()
        .entity(TargetMinecartEntity::class.java)
        .id(ResourceLocation(TACZLegacy.MOD_ID, "target_minecart"), 1)
        .name("target_minecart")
        .tracker(64, 1, true)
        .build()

    internal fun registerAll(registry: IForgeRegistry<EntityEntry>): Unit {
        registry.register(BULLET)
        registry.register(TARGET_MINECART)
    }
}

internal class EntityKineticBullet : EntityThrowable, IEntityAdditionalSpawnData {
    internal companion object {
        private const val DEFAULT_FORWARD_COMPONENT = 8.0
        private const val DEFAULT_INACCURACY_SCALE = 0.007499999832361937
        private const val FOCUSED_SMOKE_BULLET_SPEED_MULTIPLIER_PROPERTY = "tacz.focusedSmoke.bulletSpeedMultiplier"

        internal fun computeShotDirection(pitch: Double, yaw: Double, spreadX: Double, spreadY: Double): Vec3d {
            val direction = Vector3d(spreadX, spreadY, DEFAULT_FORWARD_COMPONENT)
            direction.rotateX(Math.toRadians(pitch))
            direction.rotateY(Math.toRadians(-yaw))
            val length = direction.length()
            if (length <= 1.0E-8) {
                return Vec3d.ZERO
            }
            return Vec3d(direction.x / length, direction.y / length, direction.z / length)
        }

        private fun resolveFocusedSmokeBulletSpeedMultiplier(): Double {
            val rawValue = System.getProperty(FOCUSED_SMOKE_BULLET_SPEED_MULTIPLIER_PROPERTY)?.toDoubleOrNull()
                ?: return 1.0
            if (!rawValue.isFinite() || rawValue <= 0.0) {
                return 1.0
            }
            return rawValue
        }
    }

    private var damage: Float = 5.0f
    private var bulletSpeed: Float = 5.0f
    private var bulletGravity: Float = 0.0f
    private var friction: Float = 0.01f
    private var pierce: Int = 1
    private var lifespan: Int = 200
    private var knockback: Float = 0.0f
    private var igniteEntity: Boolean = false
    private var igniteEntityTime: Int = 2
    private var igniteBlock: Boolean = false
    private var isTracerAmmo: Boolean = false
    private var damageModifier: Float = 1.0f
    private var armorIgnore: Float = 0.0f
    private var headShotMultiplier: Float = 1.0f
    private val damageAdjust: MutableList<DistanceDamagePoint> = mutableListOf()
    private var startPosX: Double = 0.0
    private var startPosY: Double = 0.0
    private var startPosZ: Double = 0.0
    private var shooterEntityId: Int = -1

    // Explosion properties
    private var hasExplosion: Boolean = false
    private var explosionRadius: Float = 0f
    private var explosionDamage: Float = 0f
    private var explosionKnockback: Boolean = false
    private var explosionDestroyBlock: Boolean = false
    private var explosionDelay: Float = 0f

    /** 发射的枪械 ID，供下游渲染/音效使用 */
    internal var gunId: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "unknown")
        private set
    /** 枪械 display ID，供下游渲染使用 */
    internal var gunDisplayId: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "default")
        private set
    /** 弹药 ID，供下游渲染/特效使用  */
    internal var ammoId: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "empty")
        private set
    internal var firstPersonRenderOffset: Vector3f? = null
    internal var firstPersonCameraPitch: Float = 0f
    internal var firstPersonCameraYaw: Float = 0f

    /** NBT / network deserialization constructor */
    constructor(worldIn: World) : super(worldIn) {
        setSize(0.1f, 0.1f)
    }

    /** 数据驱动构造器：从 BulletCombatData 获取所有弹道参数 */
    constructor(
        worldIn: World,
        shooter: EntityLivingBase,
        bulletData: BulletCombatData,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        ammoId: ResourceLocation,
        isTracer: Boolean,
    ) : super(worldIn, shooter) {
        setSize(0.1f, 0.1f)
        this.shooterEntityId = shooter.entityId
        this.damage = bulletData.damage
        this.bulletSpeed = bulletData.speed
        this.bulletGravity = bulletData.gravity
        this.friction = bulletData.friction
        this.pierce = bulletData.pierce
        this.lifespan = bulletData.getLifeTicks()
        this.knockback = bulletData.knockback
        this.igniteEntity = bulletData.igniteEntity
        this.igniteEntityTime = bulletData.igniteEntityTime
        this.igniteBlock = bulletData.igniteBlock
        this.isTracerAmmo = isTracer
        this.gunId = gunId
        this.gunDisplayId = gunDisplayId
        this.ammoId = ammoId
        resetInitialPosition(shooter)
        this.startPosX = posX
        this.startPosY = posY
        this.startPosZ = posZ

        val extraDamage = bulletData.extraDamageData
        this.armorIgnore = ((extraDamage?.armorIgnore ?: 0.0f) * LegacyConfigManager.server.armorIgnoreBaseMultiplier.toFloat())
            .coerceIn(0.0f, 1.0f)
        this.headShotMultiplier = max((extraDamage?.headShotMultiplier ?: 1.0f) * LegacyConfigManager.server.headShotBaseMultiplier.toFloat(), 0.0f)
        this.damageAdjust.clear()
        this.damageAdjust.addAll(extraDamage?.damageAdjust.orEmpty())
        
        val exp = bulletData.explosionData
        if (exp != null && exp.explode) {
            this.hasExplosion = true
            this.explosionRadius = exp.radius
            this.explosionDamage = exp.damage
            this.explosionKnockback = exp.knockback
            this.explosionDestroyBlock = exp.destroyBlock
            this.explosionDelay = if (exp.delay < 0.0f) Float.POSITIVE_INFINITY else exp.delay
        }
    }

    /** 霰弹伤害分摊。与上游 applyShotgunDamageSpread 一致。 */
    internal fun applyShotgunDamageSpread(bulletCount: Int) {
        if (bulletCount > 1) {
            damageModifier = 1.0f / bulletCount
        }
    }

    internal fun shootFromRotation(shooter: Entity, pitch: Float, yaw: Float, velocity: Float, inaccuracy: Float) {
        val spreadX = rand.nextGaussian() * DEFAULT_INACCURACY_SCALE * inaccuracy.toDouble()
        val spreadY = rand.nextGaussian() * DEFAULT_INACCURACY_SCALE * inaccuracy.toDouble()
        shootFromRotation(shooter, pitch, yaw, velocity, spreadX, spreadY)
    }

    internal fun shootFromRotation(shooter: Entity, pitch: Float, yaw: Float, velocity: Float, spreadX: Double, spreadY: Double) {
        val actualVelocity = velocity.toDouble() * resolveFocusedSmokeBulletSpeedMultiplier()
        val direction = computeShotDirection(pitch.toDouble(), yaw.toDouble(), spreadX, spreadY).scale(actualVelocity)
        motionX = direction.x
        motionY = direction.y
        motionZ = direction.z

        val horizontalDistance = sqrt(motionX * motionX + motionZ * motionZ)
        rotationYaw = Math.toDegrees(Math.atan2(motionX, motionZ)).toFloat()
        rotationPitch = Math.toDegrees(Math.atan2(motionY, horizontalDistance)).toFloat()
        prevRotationYaw = rotationYaw
        prevRotationPitch = rotationPitch

        motionX += shooter.motionX
        motionZ += shooter.motionZ
        if (!shooter.onGround) {
            motionY += shooter.motionY
        }
    }

    private fun resetInitialPosition(shooter: EntityLivingBase) {
        val interpolatedX = shooter.lastTickPosX + (shooter.posX - shooter.lastTickPosX) / 2.0
        val interpolatedY = shooter.lastTickPosY + (shooter.posY - shooter.lastTickPosY) / 2.0 + shooter.eyeHeight.toDouble()
        val interpolatedZ = shooter.lastTickPosZ + (shooter.posZ - shooter.lastTickPosZ) / 2.0
        setPosition(interpolatedX, interpolatedY, interpolatedZ)
    }

    /** 获取实际伤害（含霰弹分摊） */
    internal fun getEffectiveDamage(): Float = resolveEffectiveDamageAt(Vec3d(posX, posY, posZ))

    internal fun isTracerAmmo(): Boolean = isTracerAmmo

    internal fun getShooterForRender(): EntityLivingBase? {
        val cached = thrower
        if (cached != null && !cached.isDead) {
            return cached
        }
        if (shooterEntityId < 0) {
            return null
        }
        val resolved = world.getEntityByID(shooterEntityId) as? EntityLivingBase ?: return null
        thrower = resolved
        return resolved
    }

    override fun writeSpawnData(buffer: ByteBuf) {
        buffer.writeFloat(rotationPitch)
        buffer.writeFloat(rotationYaw)
        buffer.writeDouble(motionX)
        buffer.writeDouble(motionY)
        buffer.writeDouble(motionZ)
        ByteBufUtils.writeUTF8String(buffer, gunId.toString())
        ByteBufUtils.writeUTF8String(buffer, gunDisplayId.toString())
        ByteBufUtils.writeUTF8String(buffer, ammoId.toString())
        buffer.writeBoolean(isTracerAmmo)
        buffer.writeInt(shooterEntityId)
    }

    override fun readSpawnData(additionalData: ByteBuf) {
        rotationPitch = additionalData.readFloat()
        rotationYaw = additionalData.readFloat()
        prevRotationPitch = rotationPitch
        prevRotationYaw = rotationYaw
        motionX = additionalData.readDouble()
        motionY = additionalData.readDouble()
        motionZ = additionalData.readDouble()
        gunId = ResourceLocation(ByteBufUtils.readUTF8String(additionalData))
        gunDisplayId = ResourceLocation(ByteBufUtils.readUTF8String(additionalData))
        ammoId = ResourceLocation(ByteBufUtils.readUTF8String(additionalData))
        isTracerAmmo = additionalData.readBoolean()
        shooterEntityId = additionalData.readInt()
        getShooterForRender()
        if (world.isRemote && java.lang.Boolean.getBoolean("tacz.focusedSmoke")) {
            TACZLegacy.logger.info(
                "[FocusedSmoke] BULLET_CLIENT_SPAWN entityId={} gun={} display={} ammo={} tracer={} shooterId={} shooterResolved={}",
                entityId,
                gunId,
                gunDisplayId,
                ammoId,
                isTracerAmmo,
                shooterEntityId,
                getShooterForRender() != null,
            )
        }
    }

    override fun getGravityVelocity(): Float = bulletGravity

    override fun onImpact(result: RayTraceResult) {
        if (world.isRemote) return

        when (result.typeOfHit) {
            RayTraceResult.Type.ENTITY -> {
                val target = result.entityHit ?: return
                val hitPos = result.hitVec ?: Vec3d(target.posX, target.posY + target.height * 0.5, target.posZ)
                if (target != thrower) {
                    applyDirectHitDamage(target, hitPos)
                }
                if (hasExplosion) {
                    triggerExplosion(hitPos)
                    setDead()
                    return
                }
                pierce--
                if (pierce <= 0) setDead()
            }
            RayTraceResult.Type.BLOCK -> {
                val hitPos = result.hitVec ?: Vec3d(posX, posY, posZ)
                if (hasExplosion) {
                    triggerExplosion(hitPos)
                    setDead()
                    return
                }
                if (igniteBlock && thrower != null) {
                    val pos = result.blockPos.offset(result.sideHit)
                    if (world.isAirBlock(pos) && LegacyConfigManager.common.igniteBlock) {
                        world.setBlockState(pos, Blocks.FIRE.defaultState)
                    }
                }
                setDead()
            }
            else -> {}
        }
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        super.writeEntityToNBT(compound)
        compound.setFloat("Damage", damage)
        compound.setFloat("Speed", bulletSpeed)
        compound.setFloat("Gravity", bulletGravity)
        compound.setFloat("Friction", friction)
        compound.setInteger("Pierce", pierce)
        compound.setFloat("Knockback", knockback)
        compound.setBoolean("IgniteEntity", igniteEntity)
        compound.setInteger("IgniteEntityTime", igniteEntityTime)
        compound.setBoolean("IgniteBlock", igniteBlock)
        compound.setBoolean("IsTracer", isTracerAmmo)
        compound.setFloat("ArmorIgnore", armorIgnore)
        compound.setFloat("HeadShotMultiplier", headShotMultiplier)
        compound.setDouble("StartPosX", startPosX)
        compound.setDouble("StartPosY", startPosY)
        compound.setDouble("StartPosZ", startPosZ)
        compound.setInteger("ShooterEntityId", shooterEntityId)
        val damageAdjustList = NBTTagList()
        damageAdjust.forEach { pair ->
            val entry = NBTTagCompound()
            entry.setFloat("Distance", pair.distance)
            entry.setFloat("Damage", pair.damage)
            damageAdjustList.appendTag(entry)
        }
        compound.setTag("DamageAdjust", damageAdjustList)
        
        compound.setBoolean("HasExplosion", hasExplosion)
        if (hasExplosion) {
            compound.setFloat("ExplosionRadius", explosionRadius)
            compound.setFloat("ExplosionDamage", explosionDamage)
            compound.setBoolean("ExplosionKnockback", explosionKnockback)
            compound.setBoolean("ExplosionDestroyBlock", explosionDestroyBlock)
            compound.setBoolean("ExplosionDelayInfinite", explosionDelay.isInfinite())
            if (!explosionDelay.isInfinite()) {
                compound.setFloat("ExplosionDelay", explosionDelay)
            }
        }
        compound.setString("GunId", gunId.toString())
        compound.setString("GunDisplayId", gunDisplayId.toString())
        compound.setString("AmmoId", ammoId.toString())
        compound.setFloat("DamageModifier", damageModifier)
    }

    private fun triggerExplosion(position: Vec3d = Vec3d(posX, posY, posZ)) {
        LegacyProjectileExplosionHelper.createExplosion(
            owner = thrower,
            exploder = this,
            x = position.x,
            y = position.y,
            z = position.z,
            damage = explosionDamage,
            radius = explosionRadius,
            knockback = explosionKnockback,
            destroyBlock = explosionDestroyBlock,
        )
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        super.readEntityFromNBT(compound)
        damage = compound.getFloat("Damage")
        bulletSpeed = compound.getFloat("Speed")
        bulletGravity = compound.getFloat("Gravity")
        friction = if (compound.hasKey("Friction")) compound.getFloat("Friction") else 0.01f
        pierce = compound.getInteger("Pierce")
        knockback = if (compound.hasKey("Knockback")) compound.getFloat("Knockback") else 0.0f
        igniteEntity = compound.getBoolean("IgniteEntity")
        igniteEntityTime = if (compound.hasKey("IgniteEntityTime")) compound.getInteger("IgniteEntityTime") else 2
        igniteBlock = compound.getBoolean("IgniteBlock")
        isTracerAmmo = compound.getBoolean("IsTracer")
        armorIgnore = if (compound.hasKey("ArmorIgnore")) compound.getFloat("ArmorIgnore") else 0.0f
        headShotMultiplier = if (compound.hasKey("HeadShotMultiplier")) compound.getFloat("HeadShotMultiplier") else 1.0f
        startPosX = if (compound.hasKey("StartPosX")) compound.getDouble("StartPosX") else posX
        startPosY = if (compound.hasKey("StartPosY")) compound.getDouble("StartPosY") else posY
        startPosZ = if (compound.hasKey("StartPosZ")) compound.getDouble("StartPosZ") else posZ
        shooterEntityId = if (compound.hasKey("ShooterEntityId")) compound.getInteger("ShooterEntityId") else -1
        getShooterForRender()
        damageAdjust.clear()
        if (compound.hasKey("DamageAdjust")) {
            val list = compound.getTagList("DamageAdjust", 10)
            for (index in 0 until list.tagCount()) {
                val entry = list.getCompoundTagAt(index)
                damageAdjust += DistanceDamagePoint(
                    distance = entry.getFloat("Distance"),
                    damage = entry.getFloat("Damage"),
                )
            }
        }
        
        hasExplosion = compound.getBoolean("HasExplosion")
        if (hasExplosion) {
            explosionRadius = compound.getFloat("ExplosionRadius")
            explosionDamage = compound.getFloat("ExplosionDamage")
            explosionKnockback = compound.getBoolean("ExplosionKnockback")
            explosionDestroyBlock = compound.getBoolean("ExplosionDestroyBlock")
            explosionDelay = if (compound.getBoolean("ExplosionDelayInfinite")) {
                Float.POSITIVE_INFINITY
            } else {
                compound.getFloat("ExplosionDelay")
            }
        }
        if (compound.hasKey("GunId")) gunId = ResourceLocation(compound.getString("GunId"))
        if (compound.hasKey("GunDisplayId")) gunDisplayId = ResourceLocation(compound.getString("GunDisplayId"))
        if (compound.hasKey("AmmoId")) ammoId = ResourceLocation(compound.getString("AmmoId"))
        damageModifier = if (compound.hasKey("DamageModifier")) compound.getFloat("DamageModifier") else 1.0f
    }

    override fun onUpdate() {
        // 完全绕过 EntityThrowable.onUpdate() 的碰撞检测、0.99f 拖拽与 0.2 旋转插值。
        // 与上游 TACZ EntityKineticBullet.tick() + onBulletTick() 逻辑对齐。
        lastTickPosX = posX
        lastTickPosY = posY
        lastTickPosZ = posZ
        // Entity.onUpdate() — 只跑计时器和骑乘逻辑
        super.onEntityUpdate()

        // ---- 服务端碰撞检测 (对齐上游 onBulletTick) ----
        if (!world.isRemote) {
            if (hasExplosion && explosionDelay.isFinite()) {
                explosionDelay -= 0.05f
                if (explosionDelay <= 0 && !isDead) {
                    triggerExplosion()
                    setDead()
                    return
                }
            }

            val startVec = Vec3d(posX, posY, posZ)
            var endVec = Vec3d(posX + motionX, posY + motionY, posZ + motionZ)

            // 方块碰撞
            val blockResult = world.rayTraceBlocks(startVec, endVec)
            if (blockResult != null && blockResult.typeOfHit == RayTraceResult.Type.BLOCK) {
                endVec = blockResult.hitVec
            }

            // 实体碰撞
            val entitiesInRange = world.getEntitiesWithinAABBExcludingEntity(
                this,
                entityBoundingBox.expand(motionX, motionY, motionZ).grow(1.0),
            )
            val hitEntities = mutableListOf<Pair<Entity, Vec3d>>()
            for (entity in entitiesInRange) {
                if (!entity.canBeCollidedWith()) continue
                if (entity === thrower && ticksExisted < 5) continue
                val aabb = entity.entityBoundingBox.grow(0.3)
                val intercept = aabb.calculateIntercept(startVec, endVec)
                if (intercept != null) {
                    hitEntities += entity to intercept.hitVec
                }
            }
            // 按距离排序
            hitEntities.sortBy { (_, hitVec) -> startVec.squareDistanceTo(hitVec) }

            // 处理实体命中
            for ((entity, hitVec) in hitEntities) {
                val entityHitResult = RayTraceResult(entity, hitVec)
                if (!net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, entityHitResult)) {
                    onImpact(entityHitResult)
                }
                if (isDead) return
                if (pierce < 1) {
                    setDead()
                    return
                }
            }

            // 处理方块命中（在实体命中之后，与上游一致）
            if (blockResult != null && blockResult.typeOfHit == RayTraceResult.Type.BLOCK) {
                if (!net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, blockResult)) {
                    onImpact(blockResult)
                }
                if (isDead) return
            }
        }

        // ---- 位置与旋转更新 (对齐上游 tick) ----
        posX += motionX
        posY += motionY
        posZ += motionZ

        val horizontalDist = sqrt(motionX * motionX + motionZ * motionZ)
        val targetYaw = (Math.toDegrees(Math.atan2(motionX, motionZ))).toFloat()
        val targetPitch = (Math.toDegrees(Math.atan2(motionY, horizontalDist))).toFloat()
        if (prevRotationPitch == 0.0f && prevRotationYaw == 0.0f) {
            prevRotationYaw = targetYaw
            prevRotationPitch = targetPitch
        }
        rotationYaw = lerpRotation(prevRotationYaw, targetYaw)
        rotationPitch = lerpRotation(prevRotationPitch, targetPitch)
        prevRotationPitch = rotationPitch
        prevRotationYaw = rotationYaw

        setPosition(posX, posY, posZ)

        // ---- 阻力与重力 (只用 TACZ 的参数，不叠加 vanilla 0.99f) ----
        var frictionFactor = friction
        var gravityValue = bulletGravity
        if (isInWater) {
            frictionFactor = 0.4f
            gravityValue *= 0.6f
        }
        motionX *= (1.0 - frictionFactor)
        motionY *= (1.0 - frictionFactor)
        motionZ *= (1.0 - frictionFactor)
        motionY -= gravityValue

        // ---- 寿命检查 ----
        lifespan--
        if (lifespan <= 0) setDead()
    }

    private fun lerpRotation(previous: Float, current: Float): Float {
        var delta = MathHelper.wrapDegrees(current - previous)
        delta = delta.coerceIn(-180.0f, 180.0f)
        return previous + delta * 0.2f
    }

    private fun applyDirectHitDamage(target: Entity, hitPosition: Vec3d) {
        val totalDamage = resolveEffectiveDamageAt(hitPosition)
        if (totalDamage <= 0.0f) {
            return
        }
        val isHeadShot = target is EntityLivingBase && isHeadShot(target, hitPosition)
        val headShotDamage = if (isHeadShot) totalDamage * headShotMultiplier else totalDamage
        val damageSplit = splitDamage(headShotDamage)
        if (target is EntityLivingBase) {
            target.hurtResistantTime = 0
        }
        if (damageSplit.first > 0.0f) {
            target.attackEntityFrom(createBulletDamageSource(ignoreArmor = false), damageSplit.first)
        }
        if (damageSplit.second > 0.0f) {
            if (target is EntityLivingBase) {
                target.hurtResistantTime = 0
            }
            target.attackEntityFrom(createBulletDamageSource(ignoreArmor = true), damageSplit.second)
        }
        applyImpactKnockback(target)
        if (igniteEntity && !target.world.isRemote && LegacyConfigManager.common.igniteEntity) {
            target.setFire(igniteEntityTime)
        }
    }

    private fun createBulletDamageSource(ignoreArmor: Boolean): DamageSource {
        val damageSource = DamageSource.causeThrownDamage(this, thrower).setProjectile()
        return if (ignoreArmor) damageSource.setDamageBypassesArmor() else damageSource
    }

    private fun applyImpactKnockback(target: Entity) {
        if (knockback <= 0.0f) {
            return
        }
        val horizontalSpeed = sqrt(motionX * motionX + motionZ * motionZ)
        if (horizontalSpeed <= 1.0E-6) {
            return
        }
        val normX = motionX / horizontalSpeed
        val normZ = motionZ / horizontalSpeed
        target.addVelocity(normX * knockback * 0.6, 0.1, normZ * knockback * 0.6)
        target.velocityChanged = true
    }

    private fun resolveEffectiveDamageAt(hitPosition: Vec3d): Float {
        val origin = Vec3d(startPosX, startPosY, startPosZ)
        val travelDistance = origin.distanceTo(hitPosition)
        val adjustedDamage = if (damageAdjust.isEmpty()) {
            damage
        } else {
            damageAdjust.firstOrNull { travelDistance < it.distance || it.distance.isInfinite() }?.damage
                ?: damageAdjust.last().damage
        }
        return (adjustedDamage * LegacyConfigManager.server.damageBaseMultiplier.toFloat() * damageModifier)
            .coerceAtLeast(0.0f)
    }

    private fun splitDamage(totalDamage: Float): Pair<Float, Float> {
        val armorPiercingDamage = (totalDamage * armorIgnore.coerceIn(0.0f, 1.0f)).coerceAtLeast(0.0f)
        val normalDamage = (totalDamage - armorPiercingDamage).coerceAtLeast(0.0f)
        return normalDamage to armorPiercingDamage
    }

    private fun isHeadShot(target: EntityLivingBase, hitPosition: Vec3d): Boolean {
        return resolveHeadShotBox(target).contains(hitPosition)
    }

    private fun resolveHeadShotBox(target: EntityLivingBase): AxisAlignedBB {
        val configured = EntityList.getKey(target)?.let(HeadShotAabbConfigRead::getAabb)
        if (configured != null) {
            return configured.offset(target.posX, target.posY, target.posZ).grow(0.01)
        }
        val halfWidth = target.width / 2.0
        return AxisAlignedBB(
            target.posX - halfWidth,
            target.posY + target.eyeHeight - 0.25,
            target.posZ - halfWidth,
            target.posX + halfWidth,
            target.posY + target.eyeHeight + 0.25,
            target.posZ + halfWidth,
        ).grow(0.01)
    }
}

internal class TargetMinecartEntity : EntityMinecartEmpty {
    constructor(worldIn: World) : super(worldIn)

    constructor(worldIn: World, x: Double, y: Double, z: Double) : super(worldIn, x, y, z)
}

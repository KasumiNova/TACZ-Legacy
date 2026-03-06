package com.tacz.legacy.common.entity

import com.tacz.legacy.TACZLegacy
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityMinecartEmpty
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.DamageSource
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.RayTraceResult
import net.minecraft.world.World
import net.minecraftforge.fml.common.registry.EntityEntry
import net.minecraftforge.fml.common.registry.EntityEntryBuilder
import net.minecraftforge.registries.IForgeRegistry

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

internal class EntityKineticBullet : EntityThrowable {
    private var damage: Float = 6.0f
    private var bulletSpeed: Float = 3.0f
    private var bulletGravity: Float = 0.05f
    private var pierce: Int = 1
    private var lifespan: Int = 200

    constructor(worldIn: World) : super(worldIn) {
        setSize(0.1f, 0.1f)
    }

    constructor(worldIn: World, shooter: EntityLivingBase) : super(worldIn, shooter) {
        setSize(0.1f, 0.1f)
    }

    override fun getGravityVelocity(): Float = bulletGravity

    override fun onImpact(result: RayTraceResult) {
        if (world.isRemote) return
        when (result.typeOfHit) {
            RayTraceResult.Type.ENTITY -> {
                val target = result.entityHit
                if (target is EntityLivingBase && target != thrower) {
                    target.attackEntityFrom(DamageSource.causeThrownDamage(this, thrower), damage)
                    pierce--
                    if (pierce <= 0) setDead()
                }
            }
            RayTraceResult.Type.BLOCK -> setDead()
            else -> {}
        }
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        super.writeEntityToNBT(compound)
        compound.setFloat("Damage", damage)
        compound.setFloat("Speed", bulletSpeed)
        compound.setFloat("Gravity", bulletGravity)
        compound.setInteger("Pierce", pierce)
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        super.readEntityFromNBT(compound)
        damage = compound.getFloat("Damage")
        bulletSpeed = compound.getFloat("Speed")
        bulletGravity = compound.getFloat("Gravity")
        pierce = compound.getInteger("Pierce")
    }

    override fun onUpdate() {
        super.onUpdate()
        lifespan--
        if (lifespan <= 0) setDead()
    }
}

internal class TargetMinecartEntity : EntityMinecartEmpty {
    constructor(worldIn: World) : super(worldIn)

    constructor(worldIn: World, x: Double, y: Double, z: Double) : super(worldIn, x, y, z)
}

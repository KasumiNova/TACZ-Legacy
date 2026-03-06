package com.tacz.legacy.common.entity

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.RayTraceResult
import net.minecraft.world.World

/**
 * 枪械子弹实体。与上游 TACZ EntityKineticBullet 行为对齐。
 * 当前为最小可用实现：支持射出、飞行、命中判定。
 */
public class EntityKineticBullet : EntityThrowable {
    private var damage: Float = 6.0f
    private var speed: Float = 3.0f
    private var gravity: Float = 0.05f
    private var pierce: Int = 1
    private var lifespan: Int = 200

    public constructor(world: World) : super(world)

    public constructor(world: World, shooter: EntityLivingBase) : super(world, shooter) {
        setSize(0.1f, 0.1f)
    }

    override fun getGravityVelocity(): Float = gravity

    override fun onImpact(result: RayTraceResult) {
        if (world.isRemote) return

        when (result.typeOfHit) {
            RayTraceResult.Type.ENTITY -> {
                val target = result.entityHit
                if (target is EntityLivingBase && target != thrower) {
                    target.attackEntityFrom(
                        net.minecraft.util.DamageSource.causeThrownDamage(this, thrower),
                        damage,
                    )
                    pierce--
                    if (pierce <= 0) setDead()
                }
            }
            RayTraceResult.Type.BLOCK -> {
                setDead()
            }
            else -> {}
        }
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        super.writeEntityToNBT(compound)
        compound.setFloat("Damage", damage)
        compound.setFloat("Speed", speed)
        compound.setFloat("Gravity", gravity)
        compound.setInteger("Pierce", pierce)
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        super.readEntityFromNBT(compound)
        damage = compound.getFloat("Damage")
        speed = compound.getFloat("Speed")
        gravity = compound.getFloat("Gravity")
        pierce = compound.getInteger("Pierce")
    }

    override fun onUpdate() {
        super.onUpdate()
        lifespan--
        if (lifespan <= 0) setDead()
    }
}

package com.tacz.legacy.common.domain.event

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.eventhandler.Cancelable
import net.minecraftforge.fml.common.eventhandler.Event

/**
 * Fired when an entity is about to be hurt by a gun bullet.
 *
 * Pre: cancelable, fired before damage is applied. Modify [damage] or [headshot] to adjust.
 * Post: not cancelable, fired after damage is applied with the actual [damage] dealt.
 */
public abstract class EntityHurtByGunEvent(
    public val target: Entity,
    public val attacker: EntityLivingBase?,
    public var damage: Float,
    public val headshot: Boolean,
    public val hitPos: Vec3d,
    public val gunId: String?
) : Event() {

    @Cancelable
    public class Pre(
        target: Entity,
        attacker: EntityLivingBase?,
        damage: Float,
        headshot: Boolean,
        hitPos: Vec3d,
        gunId: String?
    ) : EntityHurtByGunEvent(target, attacker, damage, headshot, hitPos, gunId)

    public class Post(
        target: Entity,
        attacker: EntityLivingBase?,
        damage: Float,
        headshot: Boolean,
        hitPos: Vec3d,
        gunId: String?
    ) : EntityHurtByGunEvent(target, attacker, damage, headshot, hitPos, gunId)
}

/**
 * Fired when an entity is killed by a gun bullet.
 */
public class EntityKillByGunEvent(
    public val target: Entity,
    public val attacker: EntityLivingBase?,
    public val headshot: Boolean,
    public val gunId: String?
) : Event()

/**
 * Fired when a bullet hits a block.
 */
public class AmmoHitBlockEvent(
    public val hitPos: Vec3d,
    public val attacker: EntityLivingBase?,
    public val gunId: String?
) : Event()

/**
 * Fired when the player pulls the trigger (once per trigger pull,
 * regardless of burst count). Cancelable — canceling prevents the entire shot sequence.
 */
@Cancelable
public class GunShootEvent(
    public val shooter: EntityLivingBase,
    public val gunId: String?
) : Event()

/**
 * Fired for each individual bullet fired (burst mode fires this multiple times
 * per trigger pull). Cancelable — canceling skips only the current round.
 */
@Cancelable
public class GunFireEvent(
    public val shooter: EntityLivingBase,
    public val gunId: String?
) : Event()

package com.tacz.legacy.api.entity

/**
 * 击退修改接口，由 LivingEntity 通过 Mixin 实现。
 */
public interface KnockBackModifier {
    public fun resetKnockBackStrength()
    public fun getKnockBackStrength(): Double
    public fun setKnockBackStrength(strength: Double)
}

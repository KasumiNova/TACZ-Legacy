package com.tacz.legacy.common.resource

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tacz.legacy.api.DefaultAssets
import net.minecraft.util.ResourceLocation

private fun JsonObject.safeGetObject(key: String): JsonObject? =
    get(key)?.let { if (it.isJsonObject) it.asJsonObject else null }

private fun JsonElement.safeGetObject(key: String): JsonObject? =
    if (this is JsonObject) safeGetObject(key) else null

/**
 * 提供从枪包数据中提取战斗逻辑所需运行时数据的访问器。
 * 对应上游 TACZ 的 CommonGunIndex / GunData 等查询。
 */
public object GunDataAccessor {

    /**
     * 查找指定枪械的运行时战斗数据。
     */
    @JvmStatic
    public fun getGunData(gunId: ResourceLocation): GunCombatData? {
        val gun = TACZGunPackRuntimeRegistry.getSnapshot().guns[gunId] ?: return null
        return GunCombatData.fromRawJson(gun.data.raw, gun.data)
    }

    @JvmStatic
    public fun getAttachmentMeleeData(attachmentId: ResourceLocation): AttachmentMeleeCombatData? {
        if (attachmentId == DefaultAssets.EMPTY_ATTACHMENT_ID) {
            return null
        }
        val attachment = TACZGunPackRuntimeRegistry.getSnapshot().attachments[attachmentId] ?: return null
        return AttachmentMeleeCombatData.fromRawJson(attachment.data.raw)
    }
}

/**
 * 拉平的枪械战斗参数，从 gun data JSON 按需提取。
 */
public class GunCombatData private constructor(
    public val ammoId: ResourceLocation?,
    public val ammoAmount: Int,
    public val roundsPerMinute: Int,
    public val canSlide: Boolean,
    public val boltType: BoltType,
    public val drawTimeS: Float,
    public val putAwayTimeS: Float,
    public val aimTimeS: Float,
    public val sprintTimeS: Float,
    public val reloadFeedingTimeS: Float,
    public val reloadFinishingTimeS: Float,
    public val emptyReloadFeedingTimeS: Float,
    public val emptyReloadFinishingTimeS: Float,
    public val boltTimeS: Float,
    public val fireModesSet: List<String>,
    public val burstMinInterval: Float,
    public val burstCount: Int,
    public val hasHeatData: Boolean,
    public val heatMax: Float,
    public val isReloadInfinite: Boolean,
    public val reloadType: String,
    public val meleeData: GunMeleeCombatData?,
    public val bulletData: BulletCombatData,
) {
    /**
     * 获取基于 RPM 的射击间隔（毫秒）。
     */
    public fun getShootIntervalMs(): Long {
        if (roundsPerMinute <= 0) return 0L
        return (60_000L / roundsPerMinute)
    }

    public fun canSlide(): Boolean = canSlide

    public companion object {
        internal fun fromRawJson(raw: JsonObject, def: TACZGunDataDefinition): GunCombatData {
            val bolt = raw.getAsJsonPrimitive("bolt")?.asString?.let { name ->
                try { BoltType.valueOf(name.uppercase()) } catch (_: Exception) { BoltType.OPEN_BOLT }
            } ?: BoltType.OPEN_BOLT
            val canSlide = raw.getAsJsonPrimitive("can_slide")?.asBoolean
                ?: raw.getAsJsonPrimitive("canSlide")?.asBoolean
                ?: true

            val drawTime = raw.getAsJsonPrimitive("draw_time")?.asFloat ?: 0.35f
            val putAwayTime = raw.getAsJsonPrimitive("put_away_time")?.asFloat ?: 0.35f
            val sprintTime = raw.getAsJsonPrimitive("sprint_time")?.asFloat ?: 0.15f

            val reloadObj = raw.safeGetObject("reload")
            val feedObj = reloadObj?.safeGetObject("feed")
            val feedingTime = feedObj?.getAsJsonPrimitive("time")?.asFloat
                ?: reloadObj?.getAsJsonPrimitive("feeding_time")?.asFloat ?: 1.0f
            val finishingTime = reloadObj?.getAsJsonPrimitive("finishing_time")?.asFloat ?: 0.5f
            val emptyFeedingTime = reloadObj?.getAsJsonPrimitive("empty_feeding_time")?.asFloat ?: feedingTime
            val emptyFinishingTime = reloadObj?.getAsJsonPrimitive("empty_finishing_time")?.asFloat ?: finishingTime
            val isInfinite = reloadObj?.getAsJsonPrimitive("infinite")?.asBoolean ?: false
            val reloadType = reloadObj?.getAsJsonPrimitive("type")?.asString ?: "magazine"

            val boltTime = raw.getAsJsonPrimitive("bolt_time")?.asFloat ?: 0.5f

            val burstObj = raw.safeGetObject("burst")
            val burstInterval = burstObj?.getAsJsonPrimitive("min_interval")?.asFloat ?: 0.05f
            val burstCount = burstObj?.getAsJsonPrimitive("count")?.asInt ?: 3

            val fireModes = raw.getAsJsonArray("fire_mode")?.map { it.asString } ?: listOf("semi")

            val heatObj = raw.safeGetObject("heat")
            val hasHeat = heatObj != null
            val heatMax = heatObj?.getAsJsonPrimitive("max")?.asFloat ?: 100.0f

            val meleeObj = raw.safeGetObject("melee")
            val meleeData = if (meleeObj != null) {
                val cooldown = meleeObj.getAsJsonPrimitive("cooldown")?.asFloat ?: 0.5f
                val defaultObj = meleeObj.safeGetObject("default")
                val defaultData = if (defaultObj != null) {
                    GunDefaultMeleeCombatData(
                        animationType = defaultObj.getAsJsonPrimitive("animation_type")?.asString ?: "melee_push",
                        prepTime = defaultObj.getAsJsonPrimitive("prep")?.asFloat
                            ?: defaultObj.getAsJsonPrimitive("prep_time")?.asFloat
                            ?: 0.0f,
                        cooldown = defaultObj.getAsJsonPrimitive("cooldown")?.asFloat ?: 0.3f,
                        damage = defaultObj.getAsJsonPrimitive("damage")?.asFloat ?: 1.0f,
                        distance = defaultObj.getAsJsonPrimitive("distance")?.asFloat ?: 2.0f,
                        rangeAngle = defaultObj.getAsJsonPrimitive("range_angle")?.asFloat ?: 30.0f,
                        knockback = defaultObj.getAsJsonPrimitive("knockback")?.asFloat ?: 0.0f,
                    )
                } else null
                GunMeleeCombatData(cooldown = cooldown, defaultMeleeData = defaultData)
            } else null

            val bulletObj = raw.safeGetObject("bullet")
            val extraDamageObj = bulletObj?.safeGetObject("extra_damage")
            val explosionObj = bulletObj?.safeGetObject("explosion")
            val bulletCombatData = BulletCombatData(
                damage = bulletObj?.getAsJsonPrimitive("damage")?.asFloat ?: 5.0f,
                speed = bulletObj?.getAsJsonPrimitive("speed")?.asFloat ?: 5.0f,
                gravity = bulletObj?.getAsJsonPrimitive("gravity")?.asFloat ?: 0.0f,
                friction = bulletObj?.getAsJsonPrimitive("friction")?.asFloat ?: 0.01f,
                pierce = bulletObj?.getAsJsonPrimitive("pierce")?.asInt ?: 1,
                lifeSecond = bulletObj?.getAsJsonPrimitive("life")?.asFloat ?: 10.0f,
                bulletAmount = bulletObj?.getAsJsonPrimitive("bullet_amount")?.asInt ?: 1,
                knockback = bulletObj?.getAsJsonPrimitive("knockback")?.asFloat ?: 0.0f,
                tracerCountInterval = bulletObj?.getAsJsonPrimitive("tracer_count_interval")?.asInt ?: -1,
                igniteEntity = bulletObj?.safeGetObject("ignite")?.getAsJsonPrimitive("ignite_entity")?.asBoolean
                    ?: bulletObj?.getAsJsonPrimitive("ignite_entity")?.asBoolean ?: false,
                igniteEntityTime = bulletObj?.getAsJsonPrimitive("ignite_entity_time")?.asInt ?: 2,
                igniteBlock = bulletObj?.safeGetObject("ignite")?.getAsJsonPrimitive("ignite_block")?.asBoolean
                    ?: bulletObj?.getAsJsonPrimitive("ignite_block")?.asBoolean ?: false,
                extraDamageData = extraDamageObj?.let { extra ->
                    BulletExtraDamageData(
                        armorIgnore = extra.getAsJsonPrimitive("armor_ignore")?.asFloat ?: 0.0f,
                        headShotMultiplier = extra.getAsJsonPrimitive("head_shot_multiplier")?.asFloat ?: 1.0f,
                        damageAdjust = extra.getAsJsonArray("damage_adjust")
                            ?.mapNotNull { element ->
                                val obj = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
                                val damage = obj.getAsJsonPrimitive("damage")?.asFloat ?: return@mapNotNull null
                                val distanceElement = obj.get("distance") ?: return@mapNotNull null
                                val distance = when {
                                    distanceElement.isJsonPrimitive && distanceElement.asJsonPrimitive.isString -> {
                                        if (distanceElement.asString.equals("infinite", ignoreCase = true)) {
                                            Float.POSITIVE_INFINITY
                                        } else {
                                            return@mapNotNull null
                                        }
                                    }
                                    distanceElement.isJsonPrimitive && distanceElement.asJsonPrimitive.isNumber -> distanceElement.asFloat
                                    else -> return@mapNotNull null
                                }
                                DistanceDamagePoint(distance = distance, damage = damage)
                            }
                            ?.sortedBy { it.distance }
                            ?: emptyList(),
                    )
                },
                explosionData = explosionObj?.let {
                    ExplosionData(
                        explode = it.getAsJsonPrimitive("explode")?.asBoolean ?: false,
                        radius = it.getAsJsonPrimitive("radius")?.asFloat ?: 0.0f,
                        damage = it.getAsJsonPrimitive("damage")?.asFloat ?: 0.0f,
                        knockback = it.getAsJsonPrimitive("knockback")?.asBoolean ?: false,
                        destroyBlock = it.getAsJsonPrimitive("destroy_block")?.asBoolean ?: false,
                        delay = it.getAsJsonPrimitive("delay")?.asFloat ?: 0.0f,
                    )
                },
            )

            return GunCombatData(
                ammoId = def.ammoId,
                ammoAmount = def.ammoAmount,
                roundsPerMinute = def.roundsPerMinute,
                canSlide = canSlide,
                boltType = bolt,
                drawTimeS = drawTime,
                putAwayTimeS = putAwayTime,
                aimTimeS = def.aimTime,
                sprintTimeS = sprintTime,
                reloadFeedingTimeS = feedingTime,
                reloadFinishingTimeS = finishingTime,
                emptyReloadFeedingTimeS = emptyFeedingTime,
                emptyReloadFinishingTimeS = emptyFinishingTime,
                boltTimeS = boltTime,
                fireModesSet = fireModes,
                burstMinInterval = burstInterval,
                burstCount = burstCount,
                hasHeatData = hasHeat,
                heatMax = heatMax,
                isReloadInfinite = isInfinite,
                reloadType = reloadType,
                meleeData = meleeData,
                bulletData = bulletCombatData,
            )
        }
    }
}

public enum class BoltType {
    OPEN_BOLT,
    CLOSED_BOLT,
    MANUAL_ACTION,
}

public class GunMeleeCombatData(
    public val cooldown: Float,
    public val defaultMeleeData: GunDefaultMeleeCombatData?,
)

public class GunDefaultMeleeCombatData(
    public val animationType: String,
    public val prepTime: Float,
    public val cooldown: Float,
    public val damage: Float,
    public val distance: Float,
    public val rangeAngle: Float,
    public val knockback: Float,
)

public class AttachmentMeleeCombatData(
    public val prepTime: Float,
    public val cooldown: Float,
    public val damage: Float,
    public val distance: Float,
    public val rangeAngle: Float,
    public val knockback: Float,
) {
    public companion object {
        internal fun fromRawJson(raw: JsonObject): AttachmentMeleeCombatData? {
            val melee = raw.safeGetObject("melee") ?: return null
            return AttachmentMeleeCombatData(
                prepTime = melee.getAsJsonPrimitive("prep")?.asFloat ?: 0.0f,
                cooldown = melee.getAsJsonPrimitive("cooldown")?.asFloat ?: 0.0f,
                damage = melee.getAsJsonPrimitive("damage")?.asFloat ?: 0.0f,
                distance = melee.getAsJsonPrimitive("distance")?.asFloat ?: 0.0f,
                rangeAngle = melee.getAsJsonPrimitive("range_angle")?.asFloat ?: 0.0f,
                knockback = melee.getAsJsonPrimitive("knockback")?.asFloat ?: 0.0f,
            )
        }
    }
}

/**
 * 子弹战斗参数。对应上游 TACZ BulletData 结构。
 */



public data class ExplosionData(
    val explode: Boolean,
    val radius: Float,
    val damage: Float,
    val knockback: Boolean,
    val destroyBlock: Boolean,
    val delay: Float
)

public data class DistanceDamagePoint(
    val distance: Float,
    val damage: Float,
)

public data class BulletDamageSplit(
    val normalDamage: Float,
    val armorPiercingDamage: Float,
)

public class BulletExtraDamageData(
    public val armorIgnore: Float,
    public val headShotMultiplier: Float,
    public val damageAdjust: List<DistanceDamagePoint>,
) {
    public fun resolveDamage(distance: Double, fallbackDamage: Float): Float {
        if (damageAdjust.isEmpty()) {
            return fallbackDamage
        }
        for (pair in damageAdjust) {
            if (distance < pair.distance || pair.distance.isInfinite()) {
                return pair.damage
            }
        }
        return damageAdjust.last().damage
    }

    public fun splitDamage(totalDamage: Float, armorIgnoreRatio: Float = armorIgnore): BulletDamageSplit {
        val clampedRatio = armorIgnoreRatio.coerceIn(0.0f, 1.0f)
        val armorPiercingDamage = (totalDamage * clampedRatio).coerceAtLeast(0.0f)
        val normalDamage = (totalDamage - armorPiercingDamage).coerceAtLeast(0.0f)
        return BulletDamageSplit(normalDamage = normalDamage, armorPiercingDamage = armorPiercingDamage)
    }
}

public class BulletCombatData(
    /** 基础伤害 */
    public val damage: Float,
    /** 速度（m/s 概念值，启动时除以 20 成 tick 速度） */
    public val speed: Float,
    /** 每 tick 重力加速度 */
    public val gravity: Float,
    /** 空气阻力系数 */
    public val friction: Float,
    /** 穿透次数 */
    public val pierce: Int,
    /** 子弹存活时间（秒），会乘以 20 变 tick */
    public val lifeSecond: Float,
    /** 每次射击产生的弹丸数（霰弹>1） */
    public val bulletAmount: Int,
    /** 击退值 */
    public val knockback: Float,
    /** 曳光弹间隔，-1 表示无曳光弹 */
    public val tracerCountInterval: Int,
    /** 是否点燃目标实体 */
    public val igniteEntity: Boolean,
    /** 点燃实体持续时间（tick-概念，上游是秒） */
    public val igniteEntityTime: Int,
    /** 是否点燃方块 */
    public val igniteBlock: Boolean,
    /** 额外伤害数据：距离伤害曲线 / 爆头倍率 / 穿甲比例 */
    public val extraDamageData: BulletExtraDamageData? = null,
    /** 爆炸属性 */
    public val explosionData: ExplosionData? = null,
) {
    /** 是否有曳光弹 */
    public fun hasTracerAmmo(): Boolean = tracerCountInterval >= 0

    /** 计算每 tick 飞行速度：speed / 20，与上游 processedSpeed 一致 */
    public fun getProcessedSpeed(): Float = (speed / 20.0f).coerceAtLeast(0.0f)

    /** 计算子弹生存 tick 数 */
    public fun getLifeTicks(): Int = (lifeSecond * 20).toInt().coerceAtLeast(1)
}

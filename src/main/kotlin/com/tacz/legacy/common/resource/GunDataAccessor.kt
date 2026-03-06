package com.tacz.legacy.common.resource

import com.google.gson.JsonObject
import net.minecraft.util.ResourceLocation

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
}

/**
 * 拉平的枪械战斗参数，从 gun data JSON 按需提取。
 */
public class GunCombatData private constructor(
    public val ammoId: ResourceLocation?,
    public val ammoAmount: Int,
    public val roundsPerMinute: Int,
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
    public val isReloadInfinite: Boolean,
    public val reloadType: String,
    public val meleeData: GunMeleeCombatData?,
) {
    /**
     * 获取基于 RPM 的射击间隔（毫秒）。
     */
    public fun getShootIntervalMs(): Long {
        if (roundsPerMinute <= 0) return 0L
        return (60_000L / roundsPerMinute)
    }

    public companion object {
        internal fun fromRawJson(raw: JsonObject, def: TACZGunDataDefinition): GunCombatData {
            val bolt = raw.getAsJsonPrimitive("bolt")?.asString?.let { name ->
                try { BoltType.valueOf(name.uppercase()) } catch (_: Exception) { BoltType.OPEN_BOLT }
            } ?: BoltType.OPEN_BOLT

            val drawTime = raw.getAsJsonPrimitive("draw_time")?.asFloat ?: 0.35f
            val putAwayTime = raw.getAsJsonPrimitive("put_away_time")?.asFloat ?: 0.35f
            val sprintTime = raw.getAsJsonPrimitive("sprint_time")?.asFloat ?: 0.15f

            val reloadObj = raw.getAsJsonObject("reload")
            val feedObj = reloadObj?.getAsJsonObject("feed")
            val feedingTime = feedObj?.getAsJsonPrimitive("time")?.asFloat
                ?: reloadObj?.getAsJsonPrimitive("feeding_time")?.asFloat ?: 1.0f
            val finishingTime = reloadObj?.getAsJsonPrimitive("finishing_time")?.asFloat ?: 0.5f
            val emptyFeedingTime = reloadObj?.getAsJsonPrimitive("empty_feeding_time")?.asFloat ?: feedingTime
            val emptyFinishingTime = reloadObj?.getAsJsonPrimitive("empty_finishing_time")?.asFloat ?: finishingTime
            val isInfinite = reloadObj?.getAsJsonPrimitive("infinite")?.asBoolean ?: false
            val reloadType = reloadObj?.getAsJsonPrimitive("type")?.asString ?: "magazine"

            val boltTime = raw.getAsJsonPrimitive("bolt_time")?.asFloat ?: 0.5f

            val burstObj = raw.getAsJsonObject("burst")
            val burstInterval = burstObj?.getAsJsonPrimitive("min_interval")?.asFloat ?: 0.05f
            val burstCount = burstObj?.getAsJsonPrimitive("count")?.asInt ?: 3

            val fireModes = raw.getAsJsonArray("fire_mode")?.map { it.asString } ?: listOf("semi")

            val hasHeat = raw.has("heat")

            val meleeObj = raw.getAsJsonObject("melee")
            val meleeData = if (meleeObj != null) {
                val cooldown = meleeObj.getAsJsonPrimitive("cooldown")?.asFloat ?: 0.5f
                val defaultObj = meleeObj.getAsJsonObject("default")
                val defaultData = if (defaultObj != null) {
                    GunDefaultMeleeCombatData(
                        prepTime = defaultObj.getAsJsonPrimitive("prep_time")?.asFloat ?: 0.0f,
                        cooldown = defaultObj.getAsJsonPrimitive("cooldown")?.asFloat ?: 0.3f,
                        damage = defaultObj.getAsJsonPrimitive("damage")?.asFloat ?: 1.0f,
                        distance = defaultObj.getAsJsonPrimitive("distance")?.asFloat ?: 2.0f,
                        rangeAngle = defaultObj.getAsJsonPrimitive("range_angle")?.asFloat ?: 30.0f,
                    )
                } else null
                GunMeleeCombatData(cooldown = cooldown, defaultMeleeData = defaultData)
            } else null

            return GunCombatData(
                ammoId = def.ammoId,
                ammoAmount = def.ammoAmount,
                roundsPerMinute = def.roundsPerMinute,
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
                isReloadInfinite = isInfinite,
                reloadType = reloadType,
                meleeData = meleeData,
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
    public val prepTime: Float,
    public val cooldown: Float,
    public val damage: Float,
    public val distance: Float,
    public val rangeAngle: Float,
)

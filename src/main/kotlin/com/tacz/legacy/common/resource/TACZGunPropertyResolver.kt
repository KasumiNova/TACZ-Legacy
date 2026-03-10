package com.tacz.legacy.common.resource

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.api.modifier.Modifier
import com.tacz.legacy.api.modifier.ParameterizedCachePair
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

internal object TACZGunPropertyResolver {
    internal data class FireModeAdjust(
        val damage: Float = 0f,
        val rpm: Int = 0,
        val ammoSpeed: Float = 0f,
        val armorIgnore: Float = 0f,
        val headShot: Float = 0f,
        val aimInaccuracy: Float = 0f,
        val otherInaccuracy: Float = 0f,
    )

    internal data class RecoilFunctions(
        val pitch: PolynomialSplineFunction?,
        val yaw: PolynomialSplineFunction?,
    )

    private val SLUG_TAG: ResourceLocation = ResourceLocation("tacz", "intrinsic/slug")
    private val RECOIL_SPLINE_INTERPOLATOR = SplineInterpolator()

    internal fun collectAttachmentIds(stack: ItemStack, iGun: IGun): List<ResourceLocation> {
        return AttachmentType.values().mapNotNull { type ->
            if (type == AttachmentType.NONE) {
                return@mapNotNull null
            }
            val installed = iGun.getAttachmentId(stack, type)
            when {
                installed != DefaultAssets.EMPTY_ATTACHMENT_ID -> installed
                else -> iGun.getBuiltInAttachmentId(stack, type).takeIf { it != DefaultAssets.EMPTY_ATTACHMENT_ID }
            }
        }
    }

    internal fun resolveBulletAmount(stack: ItemStack, iGun: IGun, gunData: GunCombatData): Int {
        val extendedMagId = resolveCurrentAttachmentId(stack, iGun, AttachmentType.EXTENDED_MAG)
        return if (matchesAttachmentTag(extendedMagId, SLUG_TAG)) {
            1
        } else {
            gunData.bulletData.bulletAmount.coerceAtLeast(1)
        }
    }

    internal fun resolveInaccuracyProfile(stack: ItemStack, iGun: IGun): Map<String, Float> {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val gunId = iGun.getGunId(stack)
        val raw = snapshot.guns[gunId]?.data?.raw ?: return DEFAULT_INACCURACY
        val adjust = parseFireModeAdjust(raw, iGun.getFireMode(stack))
        val defaults = parseInaccuracyDefaults(raw, adjust)
        val modifiers = collectAttachmentIds(stack, iGun).mapNotNull { attachmentId ->
            @Suppress("UNCHECKED_CAST")
            snapshot.attachments[attachmentId]?.data?.modifiers?.get("inaccuracy")?.getValue() as? Map<String, Modifier>
        }
        return TACZAttachmentModifierRegistry.evalInaccuracy(modifiers, defaults)
    }

    internal fun resolveInaccuracy(
        shooter: EntityLivingBase,
        stack: ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
    ): Float {
        val profile = resolveInaccuracyProfile(stack, iGun)
        val stateKey = resolveInaccuracyStateKey(shooter)
        val base = profile[stateKey] ?: profile[INACCURACY_STAND] ?: 0f
        val heatMultiplier = resolveHeatInaccuracyMultiplier(stack, iGun, gunData)
        return (base * heatMultiplier).coerceAtLeast(0f)
    }

    internal fun resolveHeatInaccuracyMultiplier(stack: ItemStack, iGun: IGun, gunData: GunCombatData): Float {
        if (!gunData.hasHeatData || gunData.heatMax <= 0f) {
            return 1f
        }
        val heatPercentage = (iGun.getHeatAmount(stack) / gunData.heatMax).coerceIn(0f, 1f)
        return lerp(gunData.heatMinInaccuracy, gunData.heatMaxInaccuracy, heatPercentage)
    }

    internal fun resolveHeatRpmModifier(stack: ItemStack, iGun: IGun, gunData: GunCombatData): Float {
        if (!gunData.hasHeatData || gunData.heatMax <= 0f) {
            return 1f
        }
        val heatPercentage = (iGun.getHeatAmount(stack) / gunData.heatMax).coerceIn(0f, 1f)
        return lerp(gunData.heatMinRpmModifier, gunData.heatMaxRpmModifier, heatPercentage)
    }

    internal fun resolveCameraRecoil(
        stack: ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
        aimingProgress: Float,
        isCrawling: Boolean,
    ): RecoilFunctions? {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val gunId = iGun.getGunId(stack)
        val raw = snapshot.guns[gunId]?.data?.raw ?: return null
        val recoilObject = raw.jsonObject("recoil") ?: return null
        val recoilDefaults = parseRecoilDefaults(raw)
        val recoilModifiers = collectAttachmentIds(stack, iGun).mapNotNull { attachmentId ->
            snapshot.attachments[attachmentId]?.data?.modifiers?.get("recoil")?.getValue() as? TACZRecoilModifierValue
        }
        val recoilCache = TACZAttachmentModifierRegistry.evalRecoil(recoilModifiers, recoilDefaults.first, recoilDefaults.second)
        val zoom = iGun.getAimingZoom(stack).coerceAtLeast(1f)
        val aimDenominator = min(sqrt(zoom), 1.5f)
        var aimingRecoilModifier = 1f - aimingProgress + aimingProgress / aimDenominator
        if (isCrawling) {
            aimingRecoilModifier *= gunData.crawlRecoilMultiplier
        }

        val pitchModifier = recoilCache.left().eval(aimingRecoilModifier.toDouble()).toFloat()
        val yawModifier = recoilCache.right().eval(aimingRecoilModifier.toDouble()).toFloat()
        val pitchSpline = buildRecoilSpline(recoilObject.getAsJsonArray("pitch"), pitchModifier)
        val yawSpline = buildRecoilSpline(recoilObject.getAsJsonArray("yaw"), yawModifier)
        if (pitchSpline == null && yawSpline == null) {
            return null
        }
        return RecoilFunctions(pitch = pitchSpline, yaw = yawSpline)
    }

    internal fun matchesAttachmentTag(
        attachmentId: ResourceLocation,
        tagId: ResourceLocation,
        snapshot: TACZRuntimeSnapshot = TACZGunPackRuntimeRegistry.getSnapshot(),
    ): Boolean {
        if (attachmentId == DefaultAssets.EMPTY_ATTACHMENT_ID) {
            return false
        }
        return matchesAttachmentEntries(snapshot, snapshot.attachmentTags[tagId] ?: return false, attachmentId, mutableSetOf())
    }

    internal fun parseRecoilDefaults(raw: JsonObject): Pair<Float, Float> {
        val recoilObject = raw.jsonObject("recoil") ?: return 0f to 0f
        return curveMagnitude(recoilObject.getAsJsonArray("pitch")) to curveMagnitude(recoilObject.getAsJsonArray("yaw"))
    }

    private fun resolveCurrentAttachmentId(stack: ItemStack, iGun: IGun, type: AttachmentType): ResourceLocation {
        val installed = iGun.getAttachmentId(stack, type)
        if (installed != DefaultAssets.EMPTY_ATTACHMENT_ID) {
            return installed
        }
        return iGun.getBuiltInAttachmentId(stack, type)
    }

    private fun resolveInaccuracyStateKey(shooter: EntityLivingBase): String {
        val operator = IGunOperator.fromLivingEntity(shooter)
        if (operator.getSynAimingProgress() == 1.0f) {
            return INACCURACY_AIM
        }
        if (operator.getDataHolder().isCrawling) {
            return INACCURACY_LIE
        }
        if (shooter.isSneaking) {
            return INACCURACY_SNEAK
        }
        if (isMoving(shooter)) {
            return INACCURACY_MOVE
        }
        return INACCURACY_STAND
    }

    private fun isMoving(shooter: EntityLivingBase): Boolean {
        val velocity = sqrt(shooter.motionX * shooter.motionX + shooter.motionZ * shooter.motionZ)
        return velocity > 0.05
    }

    private fun matchesAttachmentEntries(
        snapshot: TACZRuntimeSnapshot,
        entries: Set<String>,
        attachmentId: ResourceLocation,
        visitedTags: MutableSet<ResourceLocation>,
    ): Boolean {
        entries.forEach { value ->
            if (value.startsWith(TAG_PREFIX)) {
                val nestedTagId = runCatching { ResourceLocation(value.substring(TAG_PREFIX.length)) }.getOrNull() ?: return@forEach
                if (!visitedTags.add(nestedTagId)) {
                    return@forEach
                }
                val nestedEntries = snapshot.attachmentTags[nestedTagId] ?: return@forEach
                if (matchesAttachmentEntries(snapshot, nestedEntries, attachmentId, visitedTags)) {
                    return true
                }
                return@forEach
            }
            val targetId = runCatching { ResourceLocation(value) }.getOrNull() ?: return@forEach
            if (targetId == attachmentId) {
                return true
            }
        }
        return false
    }

    private fun parseFireModeAdjust(raw: JsonObject, fireMode: FireMode): FireModeAdjust {
        val modeKey = when (fireMode) {
            FireMode.AUTO -> "auto"
            FireMode.SEMI -> "semi"
            FireMode.BURST -> "burst"
            FireMode.UNKNOWN -> return FireModeAdjust()
        }
        val adjustObject = raw.jsonObject("fire_mode_adjust")?.jsonObject(modeKey) ?: return FireModeAdjust()
        return FireModeAdjust(
            damage = adjustObject.floatValue("damage"),
            rpm = adjustObject.intValue("rpm"),
            ammoSpeed = adjustObject.floatValue("speed"),
            armorIgnore = adjustObject.floatValue("armor_ignore"),
            headShot = adjustObject.floatValue("head_shot_multiplier"),
            aimInaccuracy = adjustObject.floatValue("aim_inaccuracy"),
            otherInaccuracy = adjustObject.floatValue("other_inaccuracy"),
        )
    }

    private fun parseInaccuracyDefaults(raw: JsonObject, adjust: FireModeAdjust): Map<String, Float> {
        val inaccuracy = raw.jsonObject("inaccuracy")
        val standBase = inaccuracy?.floatValue(INACCURACY_STAND) ?: DEFAULT_INACCURACY[INACCURACY_STAND]!!
        return linkedMapOf(
            INACCURACY_STAND to (standBase + adjust.otherInaccuracy).coerceAtLeast(0f),
            INACCURACY_MOVE to ((inaccuracy?.floatValue(INACCURACY_MOVE) ?: standBase) + adjust.otherInaccuracy).coerceAtLeast(0f),
            INACCURACY_SNEAK to ((inaccuracy?.floatValue(INACCURACY_SNEAK) ?: standBase) + adjust.otherInaccuracy).coerceAtLeast(0f),
            INACCURACY_LIE to ((inaccuracy?.floatValue(INACCURACY_LIE) ?: standBase) + adjust.otherInaccuracy).coerceAtLeast(0f),
            INACCURACY_AIM to ((inaccuracy?.floatValue(INACCURACY_AIM) ?: DEFAULT_INACCURACY[INACCURACY_AIM]!!) + adjust.aimInaccuracy).coerceAtLeast(0f),
        )
    }

    private fun curveMagnitude(curve: JsonArray?): Float {
        val first = curve?.firstOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return 0f
        val values = first.getAsJsonArray("value")?.mapNotNull { value -> runCatching { abs(value.asFloat) }.getOrNull() }.orEmpty()
        return values.maxOrNull() ?: 0f
    }

    private fun buildRecoilSpline(curve: JsonArray?, modifier: Float): PolynomialSplineFunction? {
        val frames = curve
            ?.mapNotNull { element ->
                val frame = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
                val time = frame.get("time")?.takeIf { !it.isJsonNull }?.asDouble ?: return@mapNotNull null
                val values = frame.getAsJsonArray("value")?.mapNotNull { value -> runCatching { value.asDouble }.getOrNull() }.orEmpty()
                if (values.size < 2) {
                    return@mapNotNull null
                }
                RecoilKeyFrame(time = time, min = values[0], max = values[1])
            }
            .orEmpty()
        if (frames.isEmpty()) {
            return null
        }
        val times = DoubleArray(frames.size + 1)
        val values = DoubleArray(frames.size + 1)
        times[0] = 0.0
        values[0] = 0.0
        frames.forEachIndexed { index, frame ->
            times[index + 1] = frame.time * 1000.0 + 30.0
            values[index + 1] = (frame.min + Math.random() * (frame.max - frame.min)) * modifier
        }
        return RECOIL_SPLINE_INTERPOLATOR.interpolate(times, values)
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress.coerceIn(0f, 1f)
    }

    private data class RecoilKeyFrame(
        val time: Double,
        val min: Double,
        val max: Double,
    )

    private const val TAG_PREFIX: String = "#"
    private const val INACCURACY_STAND: String = "stand"
    private const val INACCURACY_MOVE: String = "move"
    private const val INACCURACY_SNEAK: String = "sneak"
    private const val INACCURACY_LIE: String = "lie"
    private const val INACCURACY_AIM: String = "aim"

    private val DEFAULT_INACCURACY: Map<String, Float> = linkedMapOf(
        INACCURACY_STAND to 5f,
        INACCURACY_MOVE to 5.75f,
        INACCURACY_SNEAK to 3.5f,
        INACCURACY_LIE to 2.5f,
        INACCURACY_AIM to 0.15f,
    )
}

private fun JsonObject.jsonObject(key: String): JsonObject? =
    get(key)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

private fun JsonObject.floatValue(key: String): Float =
    get(key)?.takeIf { !it.isJsonNull }?.asFloat ?: 0f

private fun JsonObject.intValue(key: String): Int =
    get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0
package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilityRuntime
import com.tacz.legacy.common.application.weapon.WeaponDefinition
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

public enum class WeaponAttachmentSlot(
    public val slotKey: String
) {
    SCOPE("SCOPE"),
    MUZZLE("MUZZLE"),
    EXTENDED_MAG("EXTENDED_MAG"),
    STOCK("STOCK"),
    GRIP("GRIP"),
    LASER("LASER");

    public companion object {
        public fun fromSlotKey(raw: String): WeaponAttachmentSlot? {
            val normalized = raw.trim().uppercase().ifBlank { return null }
            return values().firstOrNull { it.slotKey == normalized || it.name == normalized }
        }
    }
}

private fun WeaponAttachmentSnapshot.toSlotMap(): Map<WeaponAttachmentSlot, String> {
    val out = linkedMapOf<WeaponAttachmentSlot, String>()
    scopeId?.let { out[WeaponAttachmentSlot.SCOPE] = it }
    muzzleId?.let { out[WeaponAttachmentSlot.MUZZLE] = it }
    extendedMagId?.let { out[WeaponAttachmentSlot.EXTENDED_MAG] = it }
    stockId?.let { out[WeaponAttachmentSlot.STOCK] = it }
    gripId?.let { out[WeaponAttachmentSlot.GRIP] = it }
    laserId?.let { out[WeaponAttachmentSlot.LASER] = it }
    return out.toMap()
}

public data class WeaponAttachmentSnapshot(
    val scopeId: String? = null,
    val muzzleId: String? = null,
    val extendedMagId: String? = null,
    val stockId: String? = null,
    val gripId: String? = null,
    val laserId: String? = null,
    val extendedMagLevel: Int = 0
) {

    public val hasScope: Boolean get() = scopeId != null
    public val hasMuzzle: Boolean get() = muzzleId != null
    public val hasExtendedMag: Boolean get() = extendedMagId != null
    public val hasStock: Boolean get() = stockId != null
    public val hasGrip: Boolean get() = gripId != null
    public val hasLaser: Boolean get() = laserId != null

    public fun installedCount(): Int {
        var count = 0
        if (hasScope) count += 1
        if (hasMuzzle) count += 1
        if (hasExtendedMag) count += 1
        if (hasStock) count += 1
        if (hasGrip) count += 1
        if (hasLaser) count += 1
        return count
    }

    public fun hudSummaryText(): String {
        val labels = mutableListOf<String>()
        if (hasScope) labels += "SCP"
        if (hasMuzzle) labels += "MZL"
        if (hasExtendedMag) labels += if (extendedMagLevel > 0) "MAG+$extendedMagLevel" else "MAG+"
        if (hasStock) labels += "STK"
        if (hasGrip) labels += "GRP"
        if (hasLaser) labels += "LSR"
        return if (labels.isEmpty()) "ATT: NONE" else "ATT: ${labels.joinToString("/")}"
    }
}

public data class WeaponAttachmentModifiers(
    val damageAdd: Float = 0f,
    val knockbackAdd: Float = 0f,
    val armorIgnoreAdd: Float = 0f,
    val headShotMultiplierAdd: Float = 0f,
    val standInaccuracyAdd: Float = 0f,
    val moveInaccuracyAdd: Float = 0f,
    val sneakInaccuracyAdd: Float = 0f,
    val lieInaccuracyAdd: Float = 0f,
    val aimInaccuracyAdd: Float = 0f,
    val bonusMagazineSize: Int = 0
)

public object WeaponAttachmentModifierResolver {

    public fun resolve(snapshot: WeaponAttachmentSnapshot): WeaponAttachmentModifiers {
        var damageAdd = 0f
        var knockbackAdd = 0f
        var armorIgnoreAdd = 0f
        var headShotMultiplierAdd = 0f
        var standInaccuracyAdd = 0f
        var moveInaccuracyAdd = 0f
        var sneakInaccuracyAdd = 0f
        var lieInaccuracyAdd = 0f
        var aimInaccuracyAdd = 0f

        if (snapshot.hasMuzzle) {
            standInaccuracyAdd -= 0.08f
            moveInaccuracyAdd -= 0.08f
            knockbackAdd += 0.05f
        }

        if (snapshot.hasScope) {
            aimInaccuracyAdd -= 0.18f
            headShotMultiplierAdd += 0.05f
        }

        if (snapshot.hasStock) {
            standInaccuracyAdd -= 0.06f
            moveInaccuracyAdd -= 0.04f
            sneakInaccuracyAdd -= 0.03f
        }

        if (snapshot.hasGrip) {
            moveInaccuracyAdd -= 0.12f
            sneakInaccuracyAdd -= 0.08f
        }

        if (snapshot.hasLaser) {
            standInaccuracyAdd -= 0.10f
            moveInaccuracyAdd -= 0.06f
            lieInaccuracyAdd -= 0.04f
        }

        if (snapshot.hasExtendedMag) {
            damageAdd -= 0.15f
            armorIgnoreAdd -= 0.02f
        }

        val bonusMagazine = when {
            snapshot.extendedMagLevel >= 3 -> 30
            snapshot.extendedMagLevel == 2 -> 20
            snapshot.extendedMagLevel == 1 -> 10
            snapshot.hasExtendedMag -> 10
            else -> 0
        }

        return WeaponAttachmentModifiers(
            damageAdd = damageAdd,
            knockbackAdd = knockbackAdd,
            armorIgnoreAdd = armorIgnoreAdd,
            headShotMultiplierAdd = headShotMultiplierAdd,
            standInaccuracyAdd = standInaccuracyAdd,
            moveInaccuracyAdd = moveInaccuracyAdd,
            sneakInaccuracyAdd = sneakInaccuracyAdd,
            lieInaccuracyAdd = lieInaccuracyAdd,
            aimInaccuracyAdd = aimInaccuracyAdd,
            bonusMagazineSize = bonusMagazine
        )
    }
}

public data class WeaponAttachmentInstallCheck(
    val accepted: Boolean,
    val reasonCode: String? = null,
    val reasonMessage: String? = null,
    val conflictSlot: WeaponAttachmentSlot? = null,
    val conflictAttachmentId: String? = null
)

public object WeaponAttachmentConflictRules {

    public fun validateInstall(
        snapshot: WeaponAttachmentSnapshot,
        slot: WeaponAttachmentSlot,
        attachmentId: String,
        gunId: String? = null,
        definition: WeaponDefinition? = null
    ): WeaponAttachmentInstallCheck {
        val normalizedId = attachmentId.trim().lowercase().ifEmpty {
            return WeaponAttachmentInstallCheck(
                accepted = false,
                reasonCode = "INVALID_ATTACHMENT_ID",
                reasonMessage = "配件 ID 不能为空。"
            )
        }

        val normalizedAllowTypes = definition?.allowAttachmentTypes
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
        if (normalizedAllowTypes.isNotEmpty() && slot.slotKey !in normalizedAllowTypes) {
            return WeaponAttachmentInstallCheck(
                accepted = false,
                reasonCode = "SLOT_NOT_ALLOWED",
                reasonMessage = "当前武器不支持在 ${slot.name} 槽位安装配件。"
            )
        }

        val attachmentCatalog = WeaponAttachmentCompatibilityRuntime.registry().snapshot()
        val catalogType = attachmentCatalog.resolveAttachmentType(normalizedId)
        if (!catalogType.isNullOrBlank() && catalogType != slot.slotKey) {
            return WeaponAttachmentInstallCheck(
                accepted = false,
                reasonCode = "ATTACHMENT_SLOT_MISMATCH",
                reasonMessage = "配件类型与槽位不匹配：${catalogType} -> ${slot.slotKey}。"
            )
        }

        val resolvedGunId = gunId?.trim()?.lowercase()?.ifBlank { null }
            ?: definition?.gunId
        val definitionAllowEntries = definition?.allowAttachments.orEmpty()
        if (!resolvedGunId.isNullOrBlank() && !attachmentCatalog.isAttachmentAllowed(
                gunId = resolvedGunId,
                attachmentId = normalizedId,
                definitionAllowEntries = definitionAllowEntries
            )
        ) {
            return WeaponAttachmentInstallCheck(
                accepted = false,
                reasonCode = "ATTACHMENT_NOT_ALLOWED",
                reasonMessage = "当前武器不允许安装该配件。"
            )
        }

        val installed = snapshot.toSlotMap()
        val occupied = installed[slot]
        if (!occupied.isNullOrBlank() && occupied.trim().lowercase() != normalizedId) {
            return WeaponAttachmentInstallCheck(
                accepted = false,
                reasonCode = "SLOT_OCCUPIED",
                reasonMessage = "槽位 ${slot.name} 已安装配件。",
                conflictSlot = slot,
                conflictAttachmentId = occupied
            )
        }

        val newTags = inferTags(normalizedId)
        val allExistingTags = installed.entries
            .filter { it.key != slot }
            .associate { (installedSlot, id) ->
                installedSlot to inferTags(id)
            }

        val newPowerTagged = newTags.contains(TAG_POWER_DEVICE)
        if (newPowerTagged) {
            val conflict = allExistingTags.entries.firstOrNull { (_, tags) -> tags.contains(TAG_POWER_DEVICE) }
            if (conflict != null) {
                return WeaponAttachmentInstallCheck(
                    accepted = false,
                    reasonCode = "TAG_CONFLICT_POWER_DEVICE",
                    reasonMessage = "电源型配件互斥：已安装 ${conflict.value.joinToString(",")}。",
                    conflictSlot = conflict.key,
                    conflictAttachmentId = installed[conflict.key]
                )
            }
        }

        val newHighZoom = newTags.contains(TAG_OPTIC_HIGH_ZOOM)
        val newLaser = newTags.contains(TAG_LASER_POINTER)
        allExistingTags.forEach { (installedSlot, tags) ->
            val pairConflict = (newHighZoom && tags.contains(TAG_LASER_POINTER)) ||
                (newLaser && tags.contains(TAG_OPTIC_HIGH_ZOOM))
            if (pairConflict) {
                return WeaponAttachmentInstallCheck(
                    accepted = false,
                    reasonCode = "TAG_CONFLICT_OPTIC_LASER",
                    reasonMessage = "高倍镜与激光指示器互斥。",
                    conflictSlot = installedSlot,
                    conflictAttachmentId = installed[installedSlot]
                )
            }
        }

        return WeaponAttachmentInstallCheck(accepted = true)
    }

    internal fun inferTags(attachmentId: String): Set<String> {
        val normalized = attachmentId.trim().lowercase()
        if (normalized.isBlank()) {
            return emptySet()
        }
        val tags = linkedSetOf<String>()

        if (normalized.contains("scope") || normalized.contains("holo") || normalized.contains("red_dot")) {
            tags += TAG_OPTIC
        }
        if (normalized.contains("x4") || normalized.contains("x6") || normalized.contains("x8") || normalized.contains("sniper")) {
            tags += TAG_OPTIC_HIGH_ZOOM
        }
        if (normalized.contains("thermal") || normalized.contains("night")) {
            tags += TAG_POWER_DEVICE
            tags += TAG_OPTIC_THERMAL
        }
        if (normalized.contains("laser")) {
            tags += TAG_POWER_DEVICE
            tags += TAG_LASER_POINTER
        }
        if (normalized.contains("flashlight")) {
            tags += TAG_POWER_DEVICE
        }
        if (normalized.contains("suppressor") || normalized.contains("silencer")) {
            tags += TAG_MUZZLE_SILENCED
        }
        if (normalized.contains("muzzle_brake") || normalized.contains("compensator")) {
            tags += TAG_MUZZLE_CONTROL
        }
        if (normalized.contains("extended_mag") || normalized.contains("drum")) {
            tags += TAG_MAG_EXTENDED
        }

        return tags
    }

    private const val TAG_POWER_DEVICE: String = "power_device"
    private const val TAG_OPTIC: String = "optic"
    private const val TAG_OPTIC_HIGH_ZOOM: String = "optic_high_zoom"
    private const val TAG_OPTIC_THERMAL: String = "optic_thermal"
    private const val TAG_LASER_POINTER: String = "laser_pointer"
    private const val TAG_MUZZLE_SILENCED: String = "muzzle_silenced"
    private const val TAG_MUZZLE_CONTROL: String = "muzzle_control"
    private const val TAG_MAG_EXTENDED: String = "mag_extended"
}

public object WeaponItemStackRuntimeData {
    internal fun writeStringDummy(stack: net.minecraft.item.ItemStack, colorRgb: Int) {}
    internal fun writeStringDummy(stack: net.minecraft.item.ItemStack, slot: WeaponAttachmentSlot, colorRgb: Int) {}


    public const val TAG_GUN_CURRENT_AMMO_COUNT: String = "GunCurrentAmmoCount"
    public const val TAG_GUN_AMMO_RESERVE_COUNT: String = "GunAmmoReserveCount"
    public const val TAG_GUN_HAS_BULLET_IN_BARREL: String = "HasBulletInBarrel"

    private const val ATTACHMENT_KEY_BASE: String = "Attachment"
    private val ATTACHMENT_KEY_CANDIDATES: Map<String, List<String>> = mapOf(
        "SCOPE" to listOf("AttachmentSCOPE", "Attachment_SCOPE", "attachment_scope"),
        "MUZZLE" to listOf("AttachmentMUZZLE", "Attachment_MUZZLE", "attachment_muzzle"),
        "EXTENDED_MAG" to listOf("AttachmentEXTENDED_MAG", "Attachment_EXTENDED_MAG", "attachment_extended_mag"),
        "STOCK" to listOf("AttachmentSTOCK", "Attachment_STOCK", "attachment_stock"),
        "GRIP" to listOf("AttachmentGRIP", "Attachment_GRIP", "attachment_grip"),
        "LASER" to listOf("AttachmentLASER", "Attachment_LASER", "attachment_laser")
    )

    public fun readAmmoInMagazine(stack: ItemStack, defaultValue: Int): Int {
        val rootTag = stack.tagCompound ?: return defaultValue.coerceAtLeast(0)
        if (!rootTag.hasKey(TAG_GUN_CURRENT_AMMO_COUNT)) {
            return defaultValue.coerceAtLeast(0)
        }
        return rootTag.getInteger(TAG_GUN_CURRENT_AMMO_COUNT).coerceAtLeast(0)
    }

    public fun readAmmoReserve(stack: ItemStack, defaultValue: Int = 0): Int {
        val rootTag = stack.tagCompound ?: return defaultValue.coerceAtLeast(0)
        if (!rootTag.hasKey(TAG_GUN_AMMO_RESERVE_COUNT)) {
            return defaultValue.coerceAtLeast(0)
        }
        return rootTag.getInteger(TAG_GUN_AMMO_RESERVE_COUNT).coerceAtLeast(0)
    }

    public fun readHasBulletInBarrel(stack: ItemStack, defaultValue: Boolean = false): Boolean {
        val rootTag = stack.tagCompound ?: return defaultValue
        if (!rootTag.hasKey(TAG_GUN_HAS_BULLET_IN_BARREL)) {
            return defaultValue
        }
        return rootTag.getBoolean(TAG_GUN_HAS_BULLET_IN_BARREL)
    }

    public fun writeAmmoState(
        stack: ItemStack,
        ammoInMagazine: Int,
        ammoReserve: Int,
        hasBulletInBarrel: Boolean
    ) {
        val rootTag = stack.tagCompound ?: NBTTagCompound().also { stack.tagCompound = it }
        rootTag.setInteger(TAG_GUN_CURRENT_AMMO_COUNT, ammoInMagazine.coerceAtLeast(0))
        rootTag.setInteger(TAG_GUN_AMMO_RESERVE_COUNT, ammoReserve.coerceAtLeast(0))
        rootTag.setBoolean(TAG_GUN_HAS_BULLET_IN_BARREL, hasBulletInBarrel)
    }

    public fun readAttachmentSnapshot(stack: ItemStack): WeaponAttachmentSnapshot {
        val rootTag = stack.tagCompound ?: return WeaponAttachmentSnapshot()
        val scopeId = readAttachmentId(rootTag, "SCOPE")
        val muzzleId = readAttachmentId(rootTag, "MUZZLE")
        val extendedMagId = readAttachmentId(rootTag, "EXTENDED_MAG")
        val stockId = readAttachmentId(rootTag, "STOCK")
        val gripId = readAttachmentId(rootTag, "GRIP")
        val laserId = readAttachmentId(rootTag, "LASER")
        return WeaponAttachmentSnapshot(
            scopeId = scopeId,
            muzzleId = muzzleId,
            extendedMagId = extendedMagId,
            stockId = stockId,
            gripId = gripId,
            laserId = laserId,
            extendedMagLevel = resolveExtendedMagLevel(extendedMagId)
        )
    }

    public fun writeAttachment(stack: ItemStack, slot: WeaponAttachmentSlot, attachmentId: String) {
        val normalizedId = attachmentId.trim().ifBlank { return }
        val rootTag = stack.tagCompound ?: NBTTagCompound().also { stack.tagCompound = it }
        val payload = NBTTagCompound().apply {
            setString("id", normalizedId)
            setTag("tag", NBTTagCompound().apply {
                setString("AttachmentId", normalizedId)
            })
        }
        rootTag.setTag(primaryAttachmentKey(slot), payload)
    }

    public fun clearAttachment(stack: ItemStack, slot: WeaponAttachmentSlot) {
        val rootTag = stack.tagCompound ?: return
        attachmentKeyCandidates(slot).forEach { key ->
            if (rootTag.hasKey(key)) {
                rootTag.removeTag(key)
            }
        }
    }

    public fun readInstalledAttachments(snapshot: WeaponAttachmentSnapshot): Map<WeaponAttachmentSlot, String> {
        return snapshot.toSlotMap()
    }

    public fun readAttachmentId(rootTag: NBTTagCompound, type: String): String? {
        val candidates = ATTACHMENT_KEY_CANDIDATES[type]
            ?: listOf("$ATTACHMENT_KEY_BASE$type", "${ATTACHMENT_KEY_BASE}_${type}")

        for (key in candidates) {
            if (!rootTag.hasKey(key)) {
                continue
            }

            val payload = rootTag.getCompoundTag(key)
            val directId = payload.getString("id")
            if (isValidAttachmentId(directId)) {
                return directId.trim()
            }

            if (payload.hasKey("tag")) {
                val itemTag = payload.getCompoundTag("tag")
                val attachmentId = itemTag.getString("AttachmentId")
                if (isValidAttachmentId(attachmentId)) {
                    return attachmentId.trim()
                }
            }
        }

        return null
    }

    private fun primaryAttachmentKey(slot: WeaponAttachmentSlot): String = "$ATTACHMENT_KEY_BASE${slot.slotKey}"

    private fun attachmentKeyCandidates(slot: WeaponAttachmentSlot): List<String> {
        return ATTACHMENT_KEY_CANDIDATES[slot.slotKey]
            ?: listOf(primaryAttachmentKey(slot), "${ATTACHMENT_KEY_BASE}_${slot.slotKey}")
    }

    private fun resolveExtendedMagLevel(extendedMagAttachmentId: String?): Int {
        val normalized = normalizeAttachmentId(extendedMagAttachmentId) ?: return 0
        return when {
            normalized.contains("level_3") || normalized.contains("extended_mag_3") || normalized.endsWith("iii") || normalized.contains("_iii") -> 3
            normalized.contains("level_2") || normalized.contains("extended_mag_2") || normalized.endsWith("ii") || normalized.contains("_ii") -> 2
            normalized.isNotBlank() -> 1
            else -> 0
        }
    }

    private fun isValidAttachmentId(value: String?): Boolean {
        val normalized = value?.trim()?.lowercase() ?: return false
        if (normalized.isBlank()) {
            return false
        }
        if (normalized == "minecraft:air" || normalized == "air" || normalized == "tacz:empty") {
            return false
        }
        if (normalized.endsWith(":air")) {
            return false
        }
        return true
    }

    private fun normalizeAttachmentId(raw: String?): String? {
        val trimmed = raw?.trim()?.lowercase()?.ifBlank { null } ?: return null
        return trimmed
    }
}

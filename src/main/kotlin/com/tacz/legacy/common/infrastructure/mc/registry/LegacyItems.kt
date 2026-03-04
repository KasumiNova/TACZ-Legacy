package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.common.application.gunpack.GunPackRuntime
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
import com.tacz.legacy.common.application.gunpack.WeaponAttachmentCompatibilityRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoBoxItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAttachmentItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacySimpleItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import net.minecraft.item.Item

public object LegacyItems {

    @Volatile
    private var registeredStandaloneItems: List<Item>? = null

    public enum class GunCategoryResolutionSource {
        METADATA,
        HEURISTIC,
        UNKNOWN
    }

    public data class GunCategoryClassificationStats(
        val total: Int,
        val metadataMatched: Int,
        val heuristicMatched: Int,
        val unknownMatched: Int,
        val metadataHintsAvailable: Int,
        val metadataHintsUsed: Int
    )

    public val ak47: Item = LegacyGunItem(
        registryPath = LegacyContentIds.AK47,
        creativeTab = LegacyCreativeTabs.tabFor(LegacyGunTabType.RIFLE),
        extraCreativeTabs = setOf(LegacyCreativeTabs.GUN_ALL)
    )

    public val weaponDebugCore: Item = LegacySimpleItem(
        registryPath = LegacyContentIds.WEAPON_DEBUG_CORE,
        creativeTab = LegacyCreativeTabs.OTHER,
        maxStackSize = 16
    )

    public fun standalone(snapshot: GunPackRuntimeSnapshot = GunPackRuntime.registry().snapshot()): List<Item> {
        return buildStandaloneItems(snapshot)
    }

    @Synchronized
    public fun prepareRegisteredStandalone(snapshot: GunPackRuntimeSnapshot = GunPackRuntime.registry().snapshot()): List<Item> {
        registeredStandaloneItems?.let { return it }
        val created = buildStandaloneItems(snapshot)
        registeredStandaloneItems = created
        return created
    }

    public fun registeredStandalone(): List<Item> {
        return registeredStandaloneItems ?: prepareRegisteredStandalone()
    }

    @Synchronized
    internal fun clearRegisteredStandaloneForTests() {
        registeredStandaloneItems = null
    }

    private fun buildStandaloneItems(snapshot: GunPackRuntimeSnapshot): List<Item> {
        val dynamicGunItems = dynamicGunDescriptors(snapshot).map { descriptor ->
            val primaryTab = LegacyCreativeTabs.tabFor(descriptor.tabType)
            val extraTabs = if (primaryTab === LegacyCreativeTabs.GUN_ALL) {
                emptySet()
            } else {
                setOf(LegacyCreativeTabs.GUN_ALL)
            }

            LegacyGunItem(
                registryPath = descriptor.registryPath,
                creativeTab = primaryTab,
                extraCreativeTabs = extraTabs
            )
        }
        val dynamicAmmoItems = dynamicAmmoDescriptors(snapshot).flatMap { descriptor ->
            listOf(
                LegacyAmmoItem(
                    registryPath = descriptor.registryPath,
                    ammoId = descriptor.ammoId,
                    roundsPerItem = descriptor.roundsPerItem,
                    sourceId = descriptor.sourceId,
                    iconTextureAssetPath = descriptor.iconTextureAssetPath,
                    creativeTab = LegacyCreativeTabs.OTHER
                ),
                LegacyAmmoBoxItem(
                    registryPath = descriptor.boxRegistryPath,
                    ammoId = descriptor.ammoId,
                    roundsPerUse = descriptor.roundsPerUse,
                    capacity = descriptor.boxCapacity,
                    sourceId = descriptor.sourceId,
                    iconTextureAssetPath = descriptor.iconTextureAssetPath,
                    creativeTab = LegacyCreativeTabs.OTHER
                )
            )
        }
        val attachmentItems = dynamicAttachmentDescriptors().map { descriptor ->
            LegacyAttachmentItem(
                registryPath = descriptor.registryPath,
                slot = descriptor.slot,
                attachmentId = descriptor.attachmentId,
                sourceId = descriptor.sourceId,
                iconTextureAssetPath = descriptor.iconTextureAssetPath,
                creativeTab = LegacyCreativeTabs.OTHER
            )
        }

        if (dynamicGunItems.isEmpty()) {
            return listOf(ak47) + dynamicAmmoItems + attachmentItems + weaponDebugCore
        }
        return dynamicGunItems + dynamicAmmoItems + attachmentItems + weaponDebugCore
    }

    public fun dynamicGunRegistryPaths(snapshot: GunPackRuntimeSnapshot): List<String> {
        return dynamicGunDescriptors(snapshot).map { it.registryPath }
    }

    public fun dynamicStandaloneRegistryPaths(snapshot: GunPackRuntimeSnapshot): List<String> {
        return buildList {
            addAll(dynamicGunDescriptors(snapshot).map { it.registryPath })
            addAll(dynamicAmmoDescriptors(snapshot).flatMap { listOf(it.registryPath, it.boxRegistryPath) })
            addAll(dynamicAttachmentDescriptors().map { it.registryPath })
        }.distinct().sorted()
    }

    public fun classificationStats(snapshot: GunPackRuntimeSnapshot): GunCategoryClassificationStats {
        val descriptors = dynamicGunDescriptors(snapshot)
        val metadataMatched = descriptors.count { it.resolutionSource == GunCategoryResolutionSource.METADATA }
        val heuristicMatched = descriptors.count { it.resolutionSource == GunCategoryResolutionSource.HEURISTIC }
        val unknownMatched = descriptors.count { it.resolutionSource == GunCategoryResolutionSource.UNKNOWN }
        return GunCategoryClassificationStats(
            total = descriptors.size,
            metadataMatched = metadataMatched,
            heuristicMatched = heuristicMatched,
            unknownMatched = unknownMatched,
            metadataHintsAvailable = snapshot.gunTypeByGunId.size,
            metadataHintsUsed = metadataMatched
        )
    }

    internal fun dynamicGunDescriptors(snapshot: GunPackRuntimeSnapshot): List<DynamicGunDescriptor> {
        val descriptorsByPath = linkedMapOf<String, DynamicGunDescriptor>()

        snapshot.sourceIdByGunId
            .entries
            .sortedBy { it.key }
            .forEach { (gunId, sourceId) ->
                val registryPath = normalizeGunRegistryPath(gunId) ?: return@forEach
                if (registryPath == LegacyContentIds.WEAPON_DEBUG_CORE) {
                    return@forEach
                }

                val metadataTabType = LegacyGunCategoryResolver.resolveFromRawType(snapshot.gunTypeByGunId[gunId])
                val heuristicTabType = LegacyGunCategoryResolver.resolve(gunId, sourceId)
                val tabType = metadataTabType ?: heuristicTabType
                val resolutionSource = when {
                    metadataTabType != null -> GunCategoryResolutionSource.METADATA
                    tabType == LegacyGunTabType.UNKNOWN -> GunCategoryResolutionSource.UNKNOWN
                    else -> GunCategoryResolutionSource.HEURISTIC
                }

                descriptorsByPath.putIfAbsent(
                    registryPath,
                    DynamicGunDescriptor(
                        gunId = gunId,
                        sourceId = sourceId,
                        registryPath = registryPath,
                        tabType = tabType,
                        resolutionSource = resolutionSource
                    )
                )
            }

        return descriptorsByPath.values.sortedBy { it.registryPath }
    }

    private fun normalizeGunRegistryPath(rawGunId: String): String? {
        val normalized = normalizeContentPath(rawGunId)
        return normalized?.ifBlank { null }
    }

    internal fun dynamicAmmoDescriptors(snapshot: GunPackRuntimeSnapshot): List<DynamicAmmoDescriptor> {
        val attachmentSnapshot = WeaponAttachmentCompatibilityRuntime.registry().snapshot()
        val descriptorsByPath = linkedMapOf<String, DynamicAmmoDescriptor>()

        snapshot.loadedGunsByAmmoId
            .entries
            .sortedBy { it.key }
            .forEach { (ammoId, guns) ->
                val normalizedAmmoPath = normalizeContentPath(ammoId) ?: return@forEach
                val registryPath = "ammo_$normalizedAmmoPath"
                val boxRegistryPath = "ammo_box_$normalizedAmmoPath"
                val roundsPerItem = guns
                    .map { it.ammoAmount }
                    .filter { it > 0 }
                    .minOrNull()
                    ?: 30
                val boxCapacity = (roundsPerItem * 5).coerceAtLeast(roundsPerItem)
                val sourceId = guns.firstOrNull()?.sourceId
                val iconTextureAssetPath = attachmentSnapshot.resolveAmmoIconTexturePath(ammoId)
                    ?: defaultAmmoSlotTexturePath(ammoId)

                descriptorsByPath.putIfAbsent(
                    registryPath,
                    DynamicAmmoDescriptor(
                        ammoId = ammoId,
                        registryPath = registryPath,
                        boxRegistryPath = boxRegistryPath,
                        sourceId = sourceId,
                        iconTextureAssetPath = iconTextureAssetPath,
                        roundsPerItem = roundsPerItem,
                        roundsPerUse = roundsPerItem,
                        boxCapacity = boxCapacity
                    )
                )
            }

        return descriptorsByPath.values.sortedBy { it.registryPath }
    }

    internal fun dynamicAttachmentDescriptors(): List<DynamicAttachmentDescriptor> {
        val snapshot = WeaponAttachmentCompatibilityRuntime.registry().snapshot()
        val descriptorsByPath = linkedMapOf<String, DynamicAttachmentDescriptor>()

        snapshot.attachmentsById
            .values
            .sortedBy { it.attachmentId }
            .forEach { definition ->
                val slot = definition.attachmentType
                    ?.let(WeaponAttachmentSlot::fromSlotKey)
                    ?: return@forEach

                val normalizedPath = normalizeContentPath(definition.attachmentId) ?: return@forEach
                val registryPath = "attachment_$normalizedPath"

                descriptorsByPath.putIfAbsent(
                    registryPath,
                    DynamicAttachmentDescriptor(
                        attachmentId = definition.attachmentId,
                        slot = slot,
                        registryPath = registryPath,
                        sourceId = definition.sourceId,
                        iconTextureAssetPath = definition.iconTextureAssetPath
                    )
                )
            }

        return descriptorsByPath.values.sortedBy { it.registryPath }
    }

    private fun defaultAmmoSlotTexturePath(ammoId: String): String? {
        val normalized = ammoId.trim().lowercase().ifBlank { return null }
        val namespace = if (normalized.contains(':')) normalized.substringBefore(':') else "tacz"
        val path = normalized.substringAfter(':')
            .map { ch -> if (ch.isLetterOrDigit() || ch == '_' || ch == '/' || ch == '-') ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .replace("//+".toRegex(), "/")
            .trim('_', '/')
            .ifBlank { return null }
        return "assets/$namespace/textures/ammo/slot/$path.png"
    }

    private fun normalizeContentPath(raw: String): String? {
        val normalized = raw.trim().lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')
        return normalized.ifBlank { null }
    }

    internal data class DynamicGunDescriptor(
        val gunId: String,
        val sourceId: String,
        val registryPath: String,
        val tabType: LegacyGunTabType,
        val resolutionSource: GunCategoryResolutionSource
    )

    internal data class DynamicAmmoDescriptor(
        val ammoId: String,
        val registryPath: String,
        val boxRegistryPath: String,
        val sourceId: String?,
        val iconTextureAssetPath: String?,
        val roundsPerItem: Int,
        val roundsPerUse: Int,
        val boxCapacity: Int
    )

    internal data class DynamicAttachmentDescriptor(
        val attachmentId: String,
        val slot: WeaponAttachmentSlot,
        val registryPath: String,
        val sourceId: String,
        val iconTextureAssetPath: String?
    )

}

package com.tacz.legacy.common.infrastructure.mc.registry

import com.tacz.legacy.common.application.gunpack.GunPackRuntime
import com.tacz.legacy.common.application.gunpack.GunPackRuntimeSnapshot
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacySimpleItem
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
        if (dynamicGunItems.isEmpty()) {
            return listOf(ak47, weaponDebugCore)
        }
        return dynamicGunItems + weaponDebugCore
    }

    public fun dynamicGunRegistryPaths(snapshot: GunPackRuntimeSnapshot): List<String> {
        return dynamicGunDescriptors(snapshot).map { it.registryPath }
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
        val normalized = rawGunId.trim().lowercase()
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

}

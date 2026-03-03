package com.tacz.legacy.common

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackReloadCoordinator
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.entity.LegacyEntityRegistrar
import com.tacz.legacy.common.infrastructure.mc.registry.LegacyItems
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

public open class CommonProxy {

    public open fun preInit(event: FMLPreInitializationEvent) {
        val outcome = GunPackReloadCoordinator.bootstrap(
            configRoot = event.modConfigurationDirectory.toPath(),
            logger = TACZLegacy.logger
        )
        val snapshot = outcome.gunPackSnapshot
        TACZLegacy.logger.info(
            "[GunPackRuntime] snapshot: total={} loaded={} warnings={} failed={} conflicts={}",
            snapshot.totalSources,
            snapshot.loadedCount,
            snapshot.warningSources.size,
            snapshot.failedSources.size,
            snapshot.duplicateGunIdSources.size
        )
        val categoryStats = LegacyItems.classificationStats(snapshot)
        TACZLegacy.logger.info(
            "[GunPackRuntime] tab classification: total={} metadata={} heuristic={} unknown={} hints={} hintsUsed={}",
            categoryStats.total,
            categoryStats.metadataMatched,
            categoryStats.heuristicMatched,
            categoryStats.unknownMatched,
            categoryStats.metadataHintsAvailable,
            categoryStats.metadataHintsUsed
        )

        val displaySnapshot = outcome.gunDisplaySnapshot
        TACZLegacy.logger.info(
            "[GunDisplayRuntime] snapshot: total={} loaded={} failed={}",
            displaySnapshot.totalSources,
            displaySnapshot.loadedCount,
            displaySnapshot.failedSources.size
        )

        val weaponRuntimeSnapshot = outcome.weaponRuntimeSnapshot
        TACZLegacy.logger.info(
            "[WeaponRuntime] definitions={} failedMappings={}",
            weaponRuntimeSnapshot.totalDefinitions,
            weaponRuntimeSnapshot.failedGunIds.size
        )

        LegacyEntityRegistrar.registerEntities()
        LegacyNetworkHandler.init(TACZLegacy.logger)
        WeaponRuntimeMcBridge.install(TACZLegacy.logger)

        if (weaponRuntimeSnapshot.failedGunIds.isNotEmpty()) {
            val sample = weaponRuntimeSnapshot.failedGunIds
                .sorted()
                .take(FAILED_WEAPON_MAPPING_LOG_LIMIT)
            TACZLegacy.logger.warn(
                "[WeaponRuntime] Failed to map gun definitions for ids={}",
                sample
            )

            val remaining = weaponRuntimeSnapshot.failedGunIds.size - sample.size
            if (remaining > 0) {
                TACZLegacy.logger.warn(
                    "[WeaponRuntime] Failed mapping log truncated: {} more gun id(s).",
                    remaining
                )
            }
        }

        if (snapshot.hasDuplicateConflicts()) {
            val conflicts = snapshot.conflictEntries()
            conflicts
                .take(CONFLICT_LOG_LIMIT)
                .forEach { conflict ->
                    TACZLegacy.logger.warn(
                        "[GunPackRuntime] conflict gunId={} winner={} losers={}",
                        conflict.gunId,
                        conflict.winnerSourceId,
                        conflict.loserSourceIds
                    )
                }

            val remaining = conflicts.size - CONFLICT_LOG_LIMIT
            if (remaining > 0) {
                TACZLegacy.logger.warn(
                    "[GunPackRuntime] conflict log truncated: {} more conflict(s).",
                    remaining
                )
            }
        }
    }

    public open fun init(event: FMLInitializationEvent): Unit = Unit

    public open fun postInit(event: FMLPostInitializationEvent): Unit = Unit

    private companion object {
        private const val CONFLICT_LOG_LIMIT: Int = 10
        private const val FAILED_WEAPON_MAPPING_LOG_LIMIT: Int = 10
    }

}

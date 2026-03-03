package com.tacz.legacy.client.command

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.resource.GunPackExternalResourcePackManager
import com.tacz.legacy.client.render.block.LegacySpecialBlockModelRegistry
import com.tacz.legacy.client.render.item.LegacyGunItemStackRenderer
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackReloadCoordinator
import com.tacz.legacy.common.infrastructure.mc.registry.LegacyItems
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.IClientCommand

/**
 * Reload TACZ-Legacy gun pack runtime snapshots.
 *
 * Notes:
 * - This command refreshes runtime data snapshots only.
 * - If the resolved dynamic gun item list changes, vanilla/Forge item registry cannot be mutated at runtime,
 *   so a full restart is required for item registration changes to take effect.
 */
public object TaczReloadGunPackClientCommand : CommandBase(), IClientCommand {

    private const val LOG_SAMPLE_LIMIT: Int = 5

    override fun getName(): String = "tacz_reload_gunpack"

    override fun getAliases(): MutableList<String> = mutableListOf("tacz_reload_pack")

    override fun getUsage(sender: ICommandSender): String = "/tacz_reload_gunpack"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = true

    override fun allowUsageWithoutPrefix(sender: ICommandSender, message: String): Boolean = true

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val outcome = GunPackReloadCoordinator.reload(TACZLegacy.logger)
        if (outcome == null) {
            sender.sendMessage(TextComponentString("[TACZ-Legacy] Gun pack runtime is not initialized yet."))
            return
        }

        LegacyGunItemStackRenderer.invalidateRuntimeCaches()
        GunPackExternalResourcePackManager.installOrRefresh(
            logger = TACZLegacy.logger,
            forceRefreshResources = true
        )

        val gunPack = outcome.gunPackSnapshot
        val display = outcome.gunDisplaySnapshot
        val weapon = outcome.weaponRuntimeSnapshot

        sender.sendMessage(
            TextComponentString(
                "[TACZ-Legacy] Reload complete: guns=${gunPack.loadedCount}/${gunPack.totalSources}, " +
                    "display=${display.loadedCount}/${display.totalSources}, weaponDefs=${weapon.totalDefinitions}."
            )
        )

        val categoryStats = LegacyItems.classificationStats(gunPack)
        sender.sendMessage(
            TextComponentString(
                "[TACZ-Legacy] Gun tabs: total=${categoryStats.total}, metadata=${categoryStats.metadataMatched}, " +
                    "heuristic=${categoryStats.heuristicMatched}, unknown=${categoryStats.unknownMatched}, " +
                    "hints=${categoryStats.metadataHintsAvailable}."
            )
        )

        val specialModelStats = LegacySpecialBlockModelRegistry.adaptationStats()
        val specialModelSample = specialModelStats.blockRegistryPaths.take(LOG_SAMPLE_LIMIT)
        sender.sendMessage(
            TextComponentString(
                "[TACZ-Legacy] Special block model adapters=${specialModelStats.adapterCount}, " +
                    "translucent=${specialModelStats.translucentAdapterCount}, sample=$specialModelSample."
            )
        )
        val specialModelValidation = LegacySpecialBlockModelRegistry.validateModelResources()
        val missingModelSample = specialModelValidation.missingModelJsonClasspathPaths.take(LOG_SAMPLE_LIMIT)
        sender.sendMessage(
            TextComponentString(
                "[TACZ-Legacy] Special block model resources valid=${specialModelValidation.valid}/${specialModelValidation.total}, " +
                    "missing=${specialModelValidation.missing}, missingSample=$missingModelSample."
            )
        )

        if (outcome.itemRegistryChanged) {
            val added = outcome.itemRegistryDelta.addedPaths
            val removed = outcome.itemRegistryDelta.removedPaths
            val addedPreview = added.take(LOG_SAMPLE_LIMIT)
            val removedPreview = removed.take(LOG_SAMPLE_LIMIT)

            sender.sendMessage(
                TextComponentString(
                    "[TACZ-Legacy] Item registry set changed (added=${added.size}, removed=${removed.size}). " +
                        "Runtime data updated, but item registry remains locked to startup set."
                )
            )
            if (addedPreview.isNotEmpty()) {
                sender.sendMessage(TextComponentString("[TACZ-Legacy] Added sample: $addedPreview"))
            }
            if (removedPreview.isNotEmpty()) {
                sender.sendMessage(TextComponentString("[TACZ-Legacy] Removed sample: $removedPreview"))
            }
            sender.sendMessage(TextComponentString("[TACZ-Legacy] Please restart the game to apply item registration changes."))
        } else {
            sender.sendMessage(TextComponentString("[TACZ-Legacy] Item registry set unchanged; hot-reload is fully applied."))
        }
    }

}

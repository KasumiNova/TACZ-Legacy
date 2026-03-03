package com.tacz.legacy.client.command

import com.tacz.legacy.client.render.debug.WeaponConsistencyDiagnostics
import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionCorrectionReason
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.IClientCommand

/**
 * Inspect/reset weapon consistency diagnostics.
 *
 * Usage:
 * - /tacz_weapon_diag
 * - /tacz_weapon_diag status|reset|reset_all
 */
public object TaczWeaponDiagClientCommand : CommandBase(), IClientCommand {

    override fun getName(): String = "tacz_weapon_diag"

    override fun getAliases(): MutableList<String> = mutableListOf("tacz_weapon_diagnostics")

    override fun getUsage(sender: ICommandSender): String = "/tacz_weapon_diag [status|reset|reset_all]"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = true

    override fun allowUsageWithoutPrefix(sender: ICommandSender, message: String): Boolean = true

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val action = resolveAction(args.getOrNull(0)?.trim()?.lowercase())
        if (action == null) {
            val raw = args.getOrNull(0)
            sender.sendMessage(TextComponentString("[TACZ-Legacy] Unknown arg: '$raw'"))
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        if (action == Action.RESET_ALL) {
            WeaponConsistencyDiagnostics.clearAll()
            sender.sendMessage(TextComponentString("[TACZ-Legacy] Weapon diagnostics cleared for all local sessions."))
            return
        }

        val player = Minecraft.getMinecraft().player
        if (player == null) {
            sender.sendMessage(TextComponentString("[TACZ-Legacy] Local player not available."))
            return
        }

        val sessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        when (action) {
            Action.STATUS -> {
                val diagnostics = WeaponConsistencyDiagnostics.snapshot(sessionId)
                if (diagnostics == null) {
                    sender.sendMessage(TextComponentString("[TACZ-Legacy] Weapon diagnostics empty for session=$sessionId"))
                    return
                }

                sender.sendMessage(TextComponentString("[TACZ-Legacy] WeaponDiag session=$sessionId"))
                sender.sendMessage(
                    TextComponentString(
                        "[TACZ-Legacy] transitions=${diagnostics.transitionTotal} last=${diagnostics.lastTransition ?: "none"} shellEvents=${diagnostics.shellEventsObserved}"
                    )
                )
                sender.sendMessage(
                    TextComponentString(
                        "[TACZ-Legacy] shellSpawn fp[event=${diagnostics.firstPersonEventSpawnCount} fallback=${diagnostics.firstPersonFallbackSpawnCount}] tp[event=${diagnostics.thirdPersonEventSpawnCount} fallback=${diagnostics.thirdPersonFallbackSpawnCount}]"
                    )
                )
                sender.sendMessage(
                    TextComponentString(
                        "[TACZ-Legacy] drift samples=${diagnostics.driftSampleCount} detected=${diagnostics.driftDetectedCount} last=${diagnostics.driftLastFields} max=${diagnostics.driftMaxFields} reasons=${formatReasonSummary(diagnostics.correctionReasonCounts)}"
                    )
                )
            }

            Action.RESET -> {
                val removed = WeaponConsistencyDiagnostics.clearSession(sessionId)
                sender.sendMessage(
                    TextComponentString(
                        "[TACZ-Legacy] Weapon diagnostics ${if (removed) "cleared" else "already empty"} for session=$sessionId"
                    )
                )
            }

            Action.RESET_ALL -> {
                // already handled above
            }
        }
    }

    internal fun resolveAction(raw: String?): Action? = when (raw) {
        null, "status", "show", "dump" -> Action.STATUS
        "reset", "clear" -> Action.RESET
        "reset_all", "clear_all", "all" -> Action.RESET_ALL
        else -> null
    }

    internal fun formatReasonSummary(counts: Map<WeaponSessionCorrectionReason, Int>): String {
        return counts.entries
            .sortedByDescending { entry -> entry.value }
            .take(4)
            .joinToString(separator = ",") { entry -> "${entry.key.name.lowercase()}:${entry.value}" }
            .ifBlank { "none" }
    }

    internal enum class Action {
        STATUS,
        RESET,
        RESET_ALL
    }
}
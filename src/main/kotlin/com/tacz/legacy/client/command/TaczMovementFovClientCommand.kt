package com.tacz.legacy.client.command

import com.tacz.legacy.client.render.RenderPipelineRuntime
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.IClientCommand

/**
 * Toggle TACZ-Legacy movement speed FOV suppression while holding a gun.
 *
 * Usage:
 * - /tacz_movement_fov
 * - /tacz_movement_fov on|off|toggle|status
 */
public object TaczMovementFovClientCommand : CommandBase(), IClientCommand {

    override fun getName(): String = "tacz_movement_fov"

    override fun getAliases(): MutableList<String> = mutableListOf("tacz_disable_movement_fov")

    override fun getUsage(sender: ICommandSender): String = "/tacz_movement_fov [on|off|toggle|status]"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = true

    override fun allowUsageWithoutPrefix(sender: ICommandSender, message: String): Boolean = true

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val mode = args.getOrNull(0)?.trim()?.lowercase()
        val current = RenderPipelineRuntime.currentConfig().disableMovementFovEffectWhenHoldingGun

        if (mode == "status") {
            sender.sendMessage(
                TextComponentString(
                    "[TACZ-Legacy] Movement speed FOV suppression is ${if (current) "enabled" else "disabled"}."
                )
            )
            return
        }

        val next = when (mode) {
            null, "toggle" -> !current
            "on", "1", "true", "yes" -> true
            "off", "0", "false", "no" -> false
            else -> {
                sender.sendMessage(TextComponentString("[TACZ-Legacy] Unknown arg: '$mode'"))
                sender.sendMessage(TextComponentString(getUsage(sender)))
                return
            }
        }

        RenderPipelineRuntime.updateConfig { it.copy(disableMovementFovEffectWhenHoldingGun = next) }
        sender.sendMessage(
            TextComponentString(
                "[TACZ-Legacy] Movement speed FOV suppression ${if (next) "enabled" else "disabled"}."
            )
        )
    }
}

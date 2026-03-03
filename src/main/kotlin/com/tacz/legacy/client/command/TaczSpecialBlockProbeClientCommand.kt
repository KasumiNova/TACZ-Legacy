package com.tacz.legacy.client.command

import com.tacz.legacy.client.render.RenderPipelineRuntime
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.IClientCommand

/**
 * Toggle TACZ-Legacy special block model probe feature.
 *
 * Usage:
 * - /tacz_special_block_probe
 * - /tacz_special_block_probe on|off|toggle|status
 */
public object TaczSpecialBlockProbeClientCommand : CommandBase(), IClientCommand {

    override fun getName(): String = "tacz_special_block_probe"

    override fun getAliases(): MutableList<String> = mutableListOf("tacz_block_probe")

    override fun getUsage(sender: ICommandSender): String = "/tacz_special_block_probe [on|off|toggle|status]"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = true

    override fun allowUsageWithoutPrefix(sender: ICommandSender, message: String): Boolean = true

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val mode = args.getOrNull(0)?.trim()?.lowercase()
        val current = RenderPipelineRuntime.currentConfig().enableSpecialBlockModelProbe

        if (mode == "status") {
            sender.sendMessage(
                TextComponentString("[TACZ-Legacy] Special block model probe is ${if (current) "enabled" else "disabled"}.")
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

        RenderPipelineRuntime.updateConfig { it.copy(enableSpecialBlockModelProbe = next) }
        sender.sendMessage(
            TextComponentString("[TACZ-Legacy] Special block model probe ${if (next) "enabled" else "disabled"}.")
        )
    }
}

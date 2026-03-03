package com.tacz.legacy.client.command

import com.tacz.legacy.client.render.RenderPipelineRuntime
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.IClientCommand

/**
 * Toggle TACZ-Legacy debug UI overlay.
 *
 * Usage:
 * - /tacz_debug_ui
 * - /tacz_debug_ui on|off|toggle
 */
public object TaczDebugUiClientCommand : CommandBase(), IClientCommand {

    override fun getName(): String = "tacz_debug_ui"

    override fun getAliases(): MutableList<String> = mutableListOf("tacz_debug_hud")

    override fun getUsage(sender: ICommandSender): String = "/tacz_debug_ui [on|off|toggle]"

    override fun getRequiredPermissionLevel(): Int = 0

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean = true

    override fun allowUsageWithoutPrefix(sender: ICommandSender, message: String): Boolean = true

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        val mode = args.getOrNull(0)?.trim()?.lowercase()
        val current = RenderPipelineRuntime.currentConfig().enableDebugHud

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

        RenderPipelineRuntime.updateConfig { it.copy(enableDebugHud = next) }
        sender.sendMessage(TextComponentString("[TACZ-Legacy] Debug UI ${if (next) "enabled" else "disabled"}"))
    }
}

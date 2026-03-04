package com.tacz.legacy.common.infrastructure.mc.workbench

import com.tacz.legacy.common.infrastructure.mc.registry.LegacyBlocks
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import java.util.concurrent.ConcurrentHashMap

public object WeaponWorkbenchSessionRegistry {

    private val sessionsByPlayerId: MutableMap<String, WorkbenchSession> = ConcurrentHashMap()

    public data class WorkbenchSession(
        val blockPos: BlockPos,
        val dimensionId: Int,
        val gunId: String,
        val openedAtMillis: Long
    )

    public data class SessionValidationResult(
        val valid: Boolean,
        val hadSession: Boolean,
        val reasonMessage: String? = null
    )

    public fun begin(player: EntityPlayerMP, blockPos: BlockPos, gunId: String): WorkbenchSession {
        val normalizedGunId = gunId.trim().lowercase().ifBlank { "" }
        val session = WorkbenchSession(
            blockPos = blockPos,
            dimensionId = player.dimension,
            gunId = normalizedGunId,
            openedAtMillis = System.currentTimeMillis()
        )
        sessionsByPlayerId[playerKey(player)] = session
        return session
    }

    public fun end(player: EntityPlayerMP) {
        sessionsByPlayerId.remove(playerKey(player))
    }

    public fun hasActiveSession(player: EntityPlayerMP): Boolean {
        return sessionsByPlayerId.containsKey(playerKey(player))
    }

    public fun resolveHeldLegacyGunId(player: EntityPlayerMP): String? {
        val stack = resolveHeldGunStack(player) ?: return null
        return stack.item.registryName
            ?.path
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
    }

    public fun ensureValidIfSessionPresent(player: EntityPlayerMP, currentGunId: String): Boolean {
        val result = validateIfSessionPresent(player, currentGunId)
        if (!result.valid && !result.reasonMessage.isNullOrBlank()) {
            player.sendStatusMessage(TextComponentString("[TACZ] ${result.reasonMessage}"), true)
        }
        return result.valid
    }

    public fun validateIfSessionPresent(player: EntityPlayerMP, currentGunId: String): SessionValidationResult {
        val session = sessionsByPlayerId[playerKey(player)]
            ?: return SessionValidationResult(valid = true, hadSession = false)
        val normalizedGunId = currentGunId.trim().lowercase().ifBlank {
            end(player)
            return SessionValidationResult(
                valid = false,
                hadSession = true,
                reasonMessage = "改枪台会话已失效：当前未持有有效枪械"
            )
        }

        if (player.dimension != session.dimensionId) {
            end(player)
            return SessionValidationResult(
                valid = false,
                hadSession = true,
                reasonMessage = "改枪台会话已失效：维度发生变化"
            )
        }

        val blockState = player.world.getBlockState(session.blockPos)
        if (!LegacyBlocks.isWeaponWorkbenchBlock(blockState.block)) {
            end(player)
            return SessionValidationResult(
                valid = false,
                hadSession = true,
                reasonMessage = "改枪台会话已失效：工作台已不存在"
            )
        }

        val centerX = session.blockPos.x + 0.5
        val centerY = session.blockPos.y + 0.5
        val centerZ = session.blockPos.z + 0.5
        val distanceSq = player.getDistanceSq(centerX, centerY, centerZ)
        if (distanceSq > MAX_SESSION_DISTANCE_SQ) {
            end(player)
            return SessionValidationResult(
                valid = false,
                hadSession = true,
                reasonMessage = "改枪台会话已失效：距离工作台过远"
            )
        }

        if (normalizedGunId != session.gunId) {
            end(player)
            return SessionValidationResult(
                valid = false,
                hadSession = true,
                reasonMessage = "改枪台会话已失效：请保持同一把枪进行改装"
            )
        }

        return SessionValidationResult(valid = true, hadSession = true)
    }

    private fun resolveHeldGunStack(player: EntityPlayerMP): ItemStack? {
        val main = player.heldItemMainhand
        if (!main.isEmpty && main.item is LegacyGunItem) {
            return main
        }

        val off = player.heldItemOffhand
        if (!off.isEmpty && off.item is LegacyGunItem) {
            return off
        }

        return null
    }

    private fun playerKey(player: EntityPlayerMP): String = player.uniqueID.toString()

    private const val MAX_SESSION_DISTANCE_SQ: Double = 100.0
}

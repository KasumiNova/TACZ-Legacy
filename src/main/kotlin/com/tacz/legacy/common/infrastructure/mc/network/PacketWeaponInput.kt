package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponInput() : IMessage {

    public var inputCode: Byte = CODE_UNKNOWN
    public var inputSequenceId: Int = -1
    public var inputRelativeTimestampMillis: Long = -1L

    public constructor(
        inputCode: Byte,
        inputSequenceId: Int = -1,
        inputRelativeTimestampMillis: Long = -1L
    ) : this() {
        this.inputCode = inputCode
        this.inputSequenceId = inputSequenceId
        this.inputRelativeTimestampMillis = inputRelativeTimestampMillis
    }

    override fun fromBytes(buf: ByteBuf) {
        inputCode = buf.readByte()
        inputSequenceId = buf.readInt()
        inputRelativeTimestampMillis = buf.readLong()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeByte(inputCode.toInt())
        buf.writeInt(inputSequenceId)
        buf.writeLong(inputRelativeTimestampMillis)
    }

    public fun toWeaponInputOrNull(): WeaponInput? = decodeInput(inputCode)

    public class Handler : IMessageHandler<PacketWeaponInput, IMessage> {

        override fun onMessage(message: PacketWeaponInput, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player ?: return null
            val sessionId = sessionIdForPlayer(player.uniqueID.toString())
            val input = message.toWeaponInputOrNull()

            player.serverWorld.addScheduledTask {
                val sessionService = WeaponRuntimeMcBridge.sessionServiceOrNull()
                val snapshotBeforeDispatch = sessionService?.snapshot(sessionId)
                val acceptedBySequence = when {
                    input == null -> false
                    else -> shouldDispatchSequencedInput(
                        sessionId = sessionId,
                        inputSequenceId = message.inputSequenceId
                    )
                }
                val acceptedByTimestamp = when {
                    !acceptedBySequence -> false
                    input == null -> false
                    !shouldCarryRelativeTimestamp(input) -> true
                    else -> shouldAcceptTimedInput(
                        sessionId = sessionId,
                        inputRelativeTimestampMillis = message.inputRelativeTimestampMillis,
                        nowEpochMillis = System.currentTimeMillis()
                    )
                }
                val acceptedInput = acceptedBySequence && acceptedByTimestamp
                var dispatchResult: WeaponBehaviorResult? = null

                if (acceptedInput && input != null) {
                    dispatchResult = WeaponRuntimeMcBridge.dispatchInput(player, input)
                } else if (acceptedBySequence && !acceptedByTimestamp) {
                    LegacyNetworkHandler.sendWeaponBaseTimestampSyncToClient(player)
                }

                val debugSnapshot = sessionService
                    ?.debugSnapshot(sessionId)
                val correctionReason = when {
                    debugSnapshot == null -> {
                        clearTrackedInputState(sessionId)
                        WeaponSessionCorrectionReason.NO_SESSION
                    }

                    acceptedBySequence && !acceptedByTimestamp -> WeaponSessionCorrectionReason.TIMESTAMP_OUT_OF_WINDOW
                    !acceptedInput -> WeaponSessionCorrectionReason.INPUT_REJECTED
                    else -> inferAcceptedInputCorrectionReason(
                        input = input,
                        snapshotBeforeDispatch = snapshotBeforeDispatch,
                        dispatchResult = dispatchResult
                    )
                }

                if (input == WeaponInput.TriggerPressed &&
                    (!acceptedInput || (dispatchResult?.step?.shotFired != true && dispatchResult?.step?.dryFired != true))
                ) {
                    TACZLegacy.logger.debug(
                        "[WeaponInput] trigger diagnostics sid={} seq={} ts={} acceptedSeq={} acceptedTs={} reason={} beforeShotCount={} afterShotCount={}",
                        sessionId,
                        message.inputSequenceId,
                        message.inputRelativeTimestampMillis,
                        acceptedBySequence,
                        acceptedByTimestamp,
                        correctionReason,
                        snapshotBeforeDispatch?.totalShotsFired,
                        dispatchResult?.step?.snapshot?.totalShotsFired
                    )
                }

                emitAuthoritativeCorrection(
                    sessionId = sessionId,
                    debugSnapshot = debugSnapshot,
                    ackSequenceId = message.inputSequenceId,
                    correctionReason = correctionReason,
                    sendSync = { sid, snapshot ->
                        LegacyNetworkHandler.sendWeaponSessionSyncToClient(
                            player = player,
                            sessionId = sid,
                            debugSnapshot = snapshot,
                            ackSequenceId = message.inputSequenceId,
                            correctionReason = correctionReason
                        )
                    },
                    sendClear = { sid, ack, reason ->
                        LegacyNetworkHandler.sendWeaponSessionClearToClient(
                            player = player,
                            sessionId = sid,
                            ackSequenceId = ack,
                            correctionReason = reason
                        )
                    }
                )
            }
            return null
        }

    }

    public companion object {

        private const val CODE_TRIGGER_PRESSED: Byte = 0
        private const val CODE_TRIGGER_RELEASED: Byte = 1
        private const val CODE_RELOAD_PRESSED: Byte = 2
        private const val CODE_INSPECT_PRESSED: Byte = 3
        private const val CODE_UNKNOWN: Byte = -1
        private const val SEQUENCE_WRAP_HIGH_WATERMARK: Int = Int.MAX_VALUE - 1024
        private const val SEQUENCE_WRAP_LOW_WATERMARK: Int = 1024
        private const val TIMESTAMP_DRIFT_LOWER_BOUND_MS: Long = -300L
        private const val TIMESTAMP_DRIFT_UPPER_BOUND_MS: Long = 400L

        private val lastAcceptedSequenceBySessionId: MutableMap<String, Int> = linkedMapOf()
        private val serverBaseTimestampBySessionId: MutableMap<String, Long> = linkedMapOf()

        public fun fromInput(
            input: WeaponInput,
            inputSequenceId: Int = -1,
            inputRelativeTimestampMillis: Long = -1L
        ): PacketWeaponInput? {
            val code = encodeInput(input) ?: return null
            val normalizedRelativeTimestampMillis = if (shouldCarryRelativeTimestamp(input)) {
                inputRelativeTimestampMillis
            } else {
                -1L
            }
            return PacketWeaponInput(
                inputCode = code,
                inputSequenceId = inputSequenceId,
                inputRelativeTimestampMillis = normalizedRelativeTimestampMillis
            )
        }

        internal fun shouldCarryRelativeTimestamp(input: WeaponInput): Boolean =
            input == WeaponInput.TriggerPressed

        internal fun inferAcceptedInputCorrectionReason(
            input: WeaponInput?,
            snapshotBeforeDispatch: WeaponSnapshot?,
            dispatchResult: WeaponBehaviorResult?
        ): WeaponSessionCorrectionReason {
            if (input != WeaponInput.TriggerPressed) {
                return WeaponSessionCorrectionReason.INPUT_ACCEPTED
            }

            val before = snapshotBeforeDispatch ?: return WeaponSessionCorrectionReason.INPUT_ACCEPTED
            if (before.cooldownTicksRemaining > 0) {
                return WeaponSessionCorrectionReason.SHOOT_COOLDOWN
            }

            val step = dispatchResult?.step ?: return WeaponSessionCorrectionReason.INPUT_ACCEPTED
            if (step.shotFired || step.dryFired) {
                return WeaponSessionCorrectionReason.INPUT_ACCEPTED
            }

            return WeaponSessionCorrectionReason.INPUT_REJECTED
        }

        internal fun encodeInput(input: WeaponInput): Byte? = when (input) {
            WeaponInput.TriggerPressed -> CODE_TRIGGER_PRESSED
            WeaponInput.TriggerReleased -> CODE_TRIGGER_RELEASED
            WeaponInput.ReloadPressed -> CODE_RELOAD_PRESSED
            WeaponInput.InspectPressed -> CODE_INSPECT_PRESSED
            WeaponInput.Tick -> null
        }

        internal fun decodeInput(code: Byte): WeaponInput? = when (code) {
            CODE_TRIGGER_PRESSED -> WeaponInput.TriggerPressed
            CODE_TRIGGER_RELEASED -> WeaponInput.TriggerReleased
            CODE_RELOAD_PRESSED -> WeaponInput.ReloadPressed
            CODE_INSPECT_PRESSED -> WeaponInput.InspectPressed
            else -> null
        }

        internal fun emitAuthoritativeCorrection(
            sessionId: String,
            debugSnapshot: WeaponSessionDebugSnapshot?,
            ackSequenceId: Int,
            correctionReason: WeaponSessionCorrectionReason,
            sendSync: (String, WeaponSessionDebugSnapshot) -> Boolean,
            sendClear: (String, Int, WeaponSessionCorrectionReason) -> Boolean
        ): Boolean {
            val normalizedSessionId = sessionId.trim().ifBlank { return false }
            if (debugSnapshot != null) {
                return sendSync(normalizedSessionId, debugSnapshot)
            }
            return sendClear(normalizedSessionId, ackSequenceId, correctionReason)
        }

        /**
         * 对齐 TACZ「服务端权威判定」思路：客户端输入并非必然可执行，
         * 仅接受“同会话内单调前进”的序列号，拒绝重复/过旧输入。
         */
        internal fun shouldAcceptInputSequence(
            lastAcceptedSequenceId: Int?,
            inputSequenceId: Int
        ): Boolean {
            if (inputSequenceId < 0) {
                // 兼容旧包体或未携带序列号场景。
                return true
            }

            val last = lastAcceptedSequenceId ?: return true
            if (inputSequenceId == last) {
                return false
            }

            // 客户端序列在 Int.MAX_VALUE 后回绕到 0。
            if (last >= SEQUENCE_WRAP_HIGH_WATERMARK && inputSequenceId <= SEQUENCE_WRAP_LOW_WATERMARK) {
                return true
            }

            return inputSequenceId > last
        }

        internal fun shouldDispatchSequencedInput(
            sessionId: String,
            inputSequenceId: Int
        ): Boolean {
            val normalizedSessionId = sessionId.trim().ifBlank { return false }
            synchronized(lastAcceptedSequenceBySessionId) {
                val last = lastAcceptedSequenceBySessionId[normalizedSessionId]
                val accepted = shouldAcceptInputSequence(last, inputSequenceId)
                if (!accepted) {
                    return false
                }

                if (inputSequenceId >= 0) {
                    lastAcceptedSequenceBySessionId[normalizedSessionId] = inputSequenceId
                }
                return true
            }
        }

        internal fun shouldAcceptInputTiming(
            serverBaseTimestampEpochMillis: Long,
            inputRelativeTimestampMillis: Long,
            nowEpochMillis: Long,
            lowerBoundMs: Long = TIMESTAMP_DRIFT_LOWER_BOUND_MS,
            upperBoundMs: Long = TIMESTAMP_DRIFT_UPPER_BOUND_MS
        ): Boolean {
            if (inputRelativeTimestampMillis < 0L) {
                // 兼容旧包体：无时间戳时不拒绝。
                return true
            }

            val alpha = (nowEpochMillis - serverBaseTimestampEpochMillis) - inputRelativeTimestampMillis
            return alpha in lowerBoundMs..upperBoundMs
        }

        internal fun shouldAcceptTimedInput(
            sessionId: String,
            inputRelativeTimestampMillis: Long,
            nowEpochMillis: Long = System.currentTimeMillis()
        ): Boolean {
            val normalizedSessionId = sessionId.trim().ifBlank { return false }
            synchronized(serverBaseTimestampBySessionId) {
                val serverBase = serverBaseTimestampBySessionId[normalizedSessionId]
                    ?: run {
                        val inferredBase = if (inputRelativeTimestampMillis >= 0L) {
                            nowEpochMillis - inputRelativeTimestampMillis
                        } else {
                            nowEpochMillis
                        }
                        serverBaseTimestampBySessionId[normalizedSessionId] = inferredBase
                        inferredBase
                    }
                return shouldAcceptInputTiming(
                    serverBaseTimestampEpochMillis = serverBase,
                    inputRelativeTimestampMillis = inputRelativeTimestampMillis,
                    nowEpochMillis = nowEpochMillis
                )
            }
        }

        internal fun recordServerBaseTimestamp(
            sessionId: String,
            epochMillis: Long = System.currentTimeMillis()
        ): Boolean {
            val normalizedSessionId = sessionId.trim().ifBlank { return false }
            synchronized(serverBaseTimestampBySessionId) {
                serverBaseTimestampBySessionId[normalizedSessionId] = epochMillis
                return true
            }
        }

        internal fun clearTrackedInputSequence(sessionId: String): Boolean {
            val normalizedSessionId = sessionId.trim().ifBlank { return false }
            synchronized(lastAcceptedSequenceBySessionId) {
                return lastAcceptedSequenceBySessionId.remove(normalizedSessionId) != null
            }
        }

        internal fun clearTrackedInputState(sessionId: String): Boolean {
            val normalizedSessionId = sessionId.trim().ifBlank { return false }
            val removedSequence = synchronized(lastAcceptedSequenceBySessionId) {
                lastAcceptedSequenceBySessionId.remove(normalizedSessionId)
            }
            val removedBaseTimestamp = synchronized(serverBaseTimestampBySessionId) {
                serverBaseTimestampBySessionId.remove(normalizedSessionId)
            }
            return removedSequence != null || removedBaseTimestamp != null
        }

        private fun sessionIdForPlayer(playerUuid: String): String = "player:${playerUuid.trim()}"
    }

}

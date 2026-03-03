package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponInput
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.Logger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

public object LegacyNetworkHandler {

    public val INSTANCE: SimpleNetworkWrapper =
        NetworkRegistry.INSTANCE.newSimpleChannel("${TACZLegacy.MOD_ID}.legacy")

    @Volatile
    private var initialized: Boolean = false

    private var packetId: Int = 0
    private val clientInputSequence: AtomicInteger = AtomicInteger(0)
    private val clientBaseTimestampEpochMillis: AtomicLong = AtomicLong(System.currentTimeMillis())

    @Synchronized
    public fun init(logger: Logger) {
        if (initialized) {
            return
        }

        registerPacket(
            requestMessageType = PacketWeaponInput::class.java,
            messageHandler = PacketWeaponInput.Handler::class.java,
            side = Side.SERVER
        )
        registerPacket(
            requestMessageType = PacketWeaponAimState::class.java,
            messageHandler = PacketWeaponAimState.Handler::class.java,
            side = Side.SERVER
        )
        registerPacket(
            requestMessageType = PacketWeaponSessionSync::class.java,
            messageHandler = PacketWeaponSessionSync.Handler::class.java,
            side = Side.CLIENT
        )
        registerPacket(
            requestMessageType = PacketWeaponSessionClear::class.java,
            messageHandler = PacketWeaponSessionClear.Handler::class.java,
            side = Side.CLIENT
        )
        registerPacket(
            requestMessageType = PacketWeaponBaseTimestampSync::class.java,
            messageHandler = PacketWeaponBaseTimestampSync.Handler::class.java,
            side = Side.CLIENT
        )
        registerPacket(
            requestMessageType = PacketWeaponBaseTimestampAck::class.java,
            messageHandler = PacketWeaponBaseTimestampAck.Handler::class.java,
            side = Side.SERVER
        )

        initialized = true
        clientBaseTimestampEpochMillis.set(System.currentTimeMillis())
        logger.info("[Network] Legacy network channel initialized with {} packet(s).", packetId)
    }

    public fun sendWeaponInputToServer(input: WeaponInput): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        val relativeTimestampMillis = if (input == WeaponInput.TriggerPressed) {
            val now = System.currentTimeMillis()
            (now - clientBaseTimestampEpochMillis.get()).coerceAtLeast(0L)
        } else {
            -1L
        }

        val packet = PacketWeaponInput.fromInput(
            input = input,
            inputSequenceId = nextClientInputSequenceId(),
            inputRelativeTimestampMillis = relativeTimestampMillis
        ) ?: return false
        INSTANCE.sendToServer(packet)
        return true
    }

    public fun sendWeaponAimStateToServer(isAiming: Boolean): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        INSTANCE.sendToServer(PacketWeaponAimState(isAiming = isAiming))
        return true
    }

    public fun sendWeaponBaseTimestampSyncToClient(player: EntityPlayerMP): Boolean {
        if (!initialized) {
            return false
        }
        INSTANCE.sendTo(PacketWeaponBaseTimestampSync(), player)
        return true
    }

    public fun sendWeaponBaseTimestampAckToServer(): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        INSTANCE.sendToServer(PacketWeaponBaseTimestampAck())
        return true
    }

    public fun updateClientBaseTimestamp(epochMillis: Long = System.currentTimeMillis()) {
        clientBaseTimestampEpochMillis.set(epochMillis)
    }

    public fun clientBaseTimestamp(): Long = clientBaseTimestampEpochMillis.get()

    public fun sendWeaponSessionSyncToClient(
        player: EntityPlayerMP,
        sessionId: String,
        debugSnapshot: WeaponSessionDebugSnapshot,
        ackSequenceId: Int = -1,
        correctionReason: WeaponSessionCorrectionReason = WeaponSessionCorrectionReason.PERIODIC
    ): Boolean {
        if (!initialized) {
            return false
        }

        val packet = PacketWeaponSessionSync.fromDebugSnapshot(
            sessionId = sessionId,
            debugSnapshot = debugSnapshot,
            ackSequenceId = ackSequenceId,
            correctionReason = correctionReason
        )
        INSTANCE.sendTo(packet, player)
        return true
    }

    public fun sendWeaponSessionClearToClient(
        player: EntityPlayerMP,
        sessionId: String,
        ackSequenceId: Int = -1,
        correctionReason: WeaponSessionCorrectionReason = WeaponSessionCorrectionReason.NO_SESSION
    ): Boolean {
        if (!initialized) {
            return false
        }

        INSTANCE.sendTo(
            PacketWeaponSessionClear(
                sessionId = sessionId,
                ackSequenceId = ackSequenceId,
                correctionReason = correctionReason
            ),
            player
        )
        return true
    }

    private fun nextClientInputSequenceId(): Int = clientInputSequence.updateAndGet { current ->
        if (current == Int.MAX_VALUE) 0 else current + 1
    }

    private fun <REQ : IMessage, REPLY : IMessage> registerPacket(
        requestMessageType: Class<REQ>,
        messageHandler: Class<out IMessageHandler<REQ, REPLY>>,
        side: Side
    ) {
        INSTANCE.registerMessage(messageHandler, requestMessageType, packetId++, side)
    }

}

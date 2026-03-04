package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.math.BlockPos
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
            requestMessageType = PacketWeaponAttachmentAction::class.java,
            messageHandler = PacketWeaponAttachmentAction.Handler::class.java,
            side = Side.SERVER
        )
        registerPacket(
            requestMessageType = PacketWeaponAmmoDebugState::class.java,
            messageHandler = PacketWeaponAmmoDebugState.Handler::class.java,
            side = Side.SERVER
        )
        registerPacket(
            requestMessageType = PacketWeaponWorkbenchSessionControl::class.java,
            messageHandler = PacketWeaponWorkbenchSessionControl.Handler::class.java,
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
            requestMessageType = PacketWeaponWorkbenchOpenScreen::class.java,
            messageHandler = PacketWeaponWorkbenchOpenScreen.Handler::class.java,
            side = Side.CLIENT
        )
        registerPacket(
            requestMessageType = PacketWeaponWorkbenchRefreshScreen::class.java,
            messageHandler = PacketWeaponWorkbenchRefreshScreen.Handler::class.java,
            side = Side.CLIENT
        )
        registerPacket(
            requestMessageType = PacketWeaponWorkbenchCloseScreen::class.java,
            messageHandler = PacketWeaponWorkbenchCloseScreen.Handler::class.java,
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

    public fun sendWeaponAttachmentInstallToServer(slot: WeaponAttachmentSlot, attachmentId: String): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        INSTANCE.sendToServer(PacketWeaponAttachmentAction.install(slot = slot, attachmentId = attachmentId))
        return true
    }

    public fun sendWeaponAttachmentClearToServer(slot: WeaponAttachmentSlot): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        INSTANCE.sendToServer(PacketWeaponAttachmentAction.clear(slot))
        return true
    }

    public fun sendWeaponAmmoDebugStateToServer(ammoInMagazine: Int, ammoReserve: Int): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        INSTANCE.sendToServer(PacketWeaponAmmoDebugState(ammoInMagazine = ammoInMagazine, ammoReserve = ammoReserve))
        return true
    }

    public fun sendWeaponWorkbenchSessionCloseToServer(): Boolean {
        if (!initialized) {
            return false
        }
        if (!FMLCommonHandler.instance().side.isClient) {
            return false
        }

        INSTANCE.sendToServer(PacketWeaponWorkbenchSessionControl.close())
        return true
    }

    public fun sendWeaponWorkbenchOpenToClient(player: EntityPlayerMP, gunId: String, blockPos: BlockPos): Boolean {
        if (!initialized) {
            return false
        }

        INSTANCE.sendTo(
            PacketWeaponWorkbenchOpenScreen(
                gunId = gunId,
                blockX = blockPos.x,
                blockY = blockPos.y,
                blockZ = blockPos.z
            ),
            player
        )
        return true
    }

    public fun sendWeaponWorkbenchRefreshToClient(player: EntityPlayerMP): Boolean {
        if (!initialized) {
            return false
        }

        INSTANCE.sendTo(PacketWeaponWorkbenchRefreshScreen(), player)
        return true
    }

    public fun sendWeaponWorkbenchCloseToClient(player: EntityPlayerMP, reasonMessage: String = ""): Boolean {
        if (!initialized) {
            return false
        }

        INSTANCE.sendTo(PacketWeaponWorkbenchCloseScreen(reasonMessage), player)
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

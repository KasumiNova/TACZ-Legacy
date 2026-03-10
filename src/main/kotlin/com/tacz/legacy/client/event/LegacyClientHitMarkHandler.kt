package com.tacz.legacy.client.event

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.event.EntityHurtByGunEvent
import com.tacz.legacy.api.event.EntityKillByGunEvent
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.sound.TACZClientGunSoundCoordinator
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.entity.TargetMinecartEntity
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextComponentTranslation
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import java.util.Locale

internal object LegacyClientHitMarkHandler {
    private var lastTargetHitTimestamp: Long = 0L
    private var targetDamageAmount: Float = 0.0f

    @SubscribeEvent
    fun onEntityHurt(event: EntityHurtByGunEvent.Post) {
        if (event.logicalSide != Side.CLIENT) {
            return
        }
        val player = Minecraft.getMinecraft().player ?: return
        if (player != event.attacker || event.hurtEntity == null) {
            return
        }
        val display = resolveDisplay(event.gunDisplayId, event.gunId)
        LegacyHitFeedbackState.markHit()
        if (event.isHeadShot) {
            LegacyHitFeedbackState.markHeadShot()
            TACZClientGunSoundCoordinator.playHeadHitSound(player, display)
        } else {
            TACZClientGunSoundCoordinator.playFleshHitSound(player, display)
        }
        maybeDisplayTargetDamage(player, event)
        logFocusedSmokeHit(player, event.gunId, event.isHeadShot, event.amount, killed = false)
    }

    @SubscribeEvent
    fun onEntityKill(event: EntityKillByGunEvent) {
        if (event.logicalSide != Side.CLIENT) {
            return
        }
        val player = Minecraft.getMinecraft().player ?: return
        if (player != event.attacker) {
            return
        }
        val display = resolveDisplay(event.gunDisplayId, event.gunId)
        val timeoutMs = (LegacyConfigManager.client.killAmountDurationSecond * 1000.0).toLong().coerceAtLeast(0L)
        val killAmount = LegacyHitFeedbackState.markKill(timeoutMs)
        if (event.isHeadShot) {
            LegacyHitFeedbackState.markHeadShot()
        }
        TACZClientGunSoundCoordinator.playKillSound(player, display)
        logFocusedSmokeHit(player, event.gunId, event.isHeadShot, event.amount, killed = true, killAmount = killAmount)
    }

    private fun maybeDisplayTargetDamage(player: EntityPlayerSP, event: EntityHurtByGunEvent.Post) {
        val hurtEntity = event.hurtEntity
        if (hurtEntity !is TargetMinecartEntity) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTargetHitTimestamp < LegacyConfigManager.client.damageCounterResetTime) {
            targetDamageAmount += event.amount
        } else {
            targetDamageAmount = event.amount
        }
        val distance = player.getDistance(hurtEntity)
        player.sendStatusMessage(
            TextComponentTranslation(
                "message.tacz.target_minecart.hit",
                String.format(Locale.ROOT, "%.1f", targetDamageAmount),
                String.format(Locale.ROOT, "%.2f", distance),
            ),
            true,
        )
        lastTargetHitTimestamp = now
    }

    private fun resolveDisplay(gunDisplayId: ResourceLocation, gunId: ResourceLocation): GunDisplayInstance? {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val fallbackDisplayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId)
        val resolvedDisplayId = if (gunDisplayId != DefaultAssets.DEFAULT_GUN_DISPLAY_ID) gunDisplayId else fallbackDisplayId ?: gunDisplayId
        return TACZClientAssetManager.getGunDisplayInstance(resolvedDisplayId)
    }

    private fun logFocusedSmokeHit(
        player: EntityPlayerSP,
        gunId: ResourceLocation,
        headShot: Boolean,
        amount: Float,
        killed: Boolean,
        killAmount: Int = LegacyHitFeedbackState.currentKillAmount(),
    ) {
        if (!java.lang.Boolean.getBoolean("tacz.focusedSmoke")) {
            return
        }
        TACZLegacy.logger.info(
            "[FocusedSmoke] {} gun={} player={} headShot={} amount={} killAmount={}",
            if (killed) "KILL_FEEDBACK_TRIGGERED" else "HIT_FEEDBACK_TRIGGERED",
            gunId,
            player.name,
            headShot,
            String.format(Locale.ROOT, "%.2f", amount),
            killAmount,
        )
    }
}
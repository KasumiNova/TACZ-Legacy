package com.tacz.legacy.client.render.debug

import com.tacz.legacy.client.render.RenderPipelineRuntime
import com.tacz.legacy.client.render.frame.FramePhase
import com.tacz.legacy.client.render.weapon.WeaponVisualSampleRegistry
import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionSyncClientRegistry
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.Locale

public class RenderDebugOverlayHandler {

    @SubscribeEvent
    public fun onRenderOverlayText(event: RenderGameOverlayEvent.Text) {
        if (Minecraft.getMinecraft().world == null) {
            return
        }

        val config = RenderPipelineRuntime.currentConfig()
        if (!config.enableDebugHud) {
            return
        }

        val context = RenderPipelineRuntime.latestContext() ?: return

        val left = event.left
        left += "[TACZ-Legacy] RenderPipeline R1 (event-driven)"
        left += "frame=${context.frameId} features=${RenderPipelineRuntime.coordinator().listRegisteredFeatures().size}"
        left += "active=${RenderPipelineRuntime.hasActiveFrame()}"
        left += "expected=${RenderPipelineRuntime.expectedPhase()?.name ?: "none"}"

        val totalNs = (context.diagnostics["frame.total.ns"] as? Long) ?: 0L
        left += "total=${formatNsToMs(totalNs)}"
        left += "skipped=${context.diagnostics["pipeline.skipped"] == true}"
        left += "aborted=${context.diagnostics["frame.aborted"] ?: "none"}"
        left += "compensated=${context.diagnostics["frame.compensated"] ?: "none"}"

        val drawSubmitted = (context.diagnostics["draw.command.submitted"] as? Int) ?: 0
        val drawExecuted = (context.diagnostics["draw.command.executed"] as? Int) ?: 0
        val drawFailed = (context.diagnostics["draw.command.failed"] as? Int) ?: 0
        val drawConsumed = (context.diagnostics["draw.command.consumed"] as? Int) ?: 0
        val drawBufferSize = (context.diagnostics["draw.command.buffer.size"] as? Int) ?: context.commandBuffer.size()
        left += "draw[sub=$drawSubmitted exe=$drawExecuted fail=$drawFailed consumed=$drawConsumed buf=$drawBufferSize]"

        FramePhase.defaultOrder().forEach { phase ->
            val ns = (context.diagnostics["phase.${phase.name}.ns"] as? Long) ?: 0L
            left += "${phase.name}: ${formatNsToMs(ns)}"
        }

        appendSpecialBlockProbeDebug(left, context)
        appendWeaponRuntimeDebug(left, context)
    }

    private fun appendSpecialBlockProbeDebug(
        left: MutableList<String>,
        context: com.tacz.legacy.client.render.core.RenderContext
    ) {
        val probeEnabled = context.pipelineConfig.enableSpecialBlockModelProbe
        val active = context.diagnostics["block.model_probe.active"]
        val blockPath = context.diagnostics["block.model_probe.block_path"] ?: "none"
        val modelPath = context.diagnostics["block.model_probe.last_model"] ?: "none"
        val renderTag = context.diagnostics["block.model_probe.last_render_tag"] ?: "none"
        val renderMode = context.diagnostics["block.model_probe.last_render_mode"] ?: "none"
        val skippedReason = context.diagnostics["block.model_probe.skipped_reason"] ?: "none"
        val queued = context.diagnostics["block.model_probe.queued"] ?: 0
        val rendered = context.diagnostics["block.model_probe.rendered"] ?: 0
        val skipped = context.diagnostics["block.model_probe.skipped"] ?: 0
        left += "[BlockModelProbe] enabled=$probeEnabled active=$active block=$blockPath reason=$skippedReason"
        left += "[BlockModelProbe] queued=$queued rendered=$rendered skipped=$skipped mode=$renderMode"
        left += "[BlockModelProbe] model=$modelPath tag=$renderTag"
    }

    private fun appendWeaponRuntimeDebug(left: MutableList<String>, context: com.tacz.legacy.client.render.core.RenderContext) {
        val runtimeSnapshot = WeaponRuntime.registry().snapshot()
        left += "[WeaponRuntime] defs=${runtimeSnapshot.totalDefinitions} failed=${runtimeSnapshot.failedGunIds.size}"

        val modelGateOpen = context.diagnostics["weapon.model_probe.gate_open"] as? Boolean
        if (modelGateOpen != null) {
            val gunId = context.diagnostics["weapon.model_probe.gun_id"] ?: "none"
            val modelReady = context.diagnostics["weapon.model_probe.model_ready"] ?: false
            val animReady = context.diagnostics["weapon.model_probe.animation_ready"] ?: false
            val visualReady = context.diagnostics["weapon.model_probe.visual_ready"] ?: false
            val blockedReason = context.diagnostics["weapon.model_probe.blocked_reason"] ?: "none"
            val queued = context.diagnostics["weapon.model_submit.queued"] ?: 0
            val executed = context.diagnostics["weapon.model_submit.executed"] ?: 0
            val previewRendered = context.diagnostics["weapon.model_preview.rendered"] ?: 0
            val previewTexture = context.diagnostics["weapon.model_preview.texture_path"] ?: "none"
            val previewBound = context.diagnostics["weapon.model_preview.bound_resource"] ?: "none"
            val previewSkippedReason = context.diagnostics["weapon.model_preview.skipped_reason"] ?: "none"
            left += "[WeaponModelGate] gun=$gunId open=$modelGateOpen model=$modelReady anim=$animReady visual=$visualReady reason=$blockedReason"
            left += "[WeaponModelGate] queued=$queued executed=$executed model=${context.diagnostics["weapon.model_submit.last_model_path"] ?: "none"}"
            left += "[WeaponModelPreview] rendered=$previewRendered texture=$previewTexture bound=$previewBound skipped=$previewSkippedReason"
        }

        val displaySnapshot = GunDisplayRuntime.registry().snapshot()
        left += "[GunDisplay] defs=${displaySnapshot.loadedCount} failed=${displaySnapshot.failedSources.size}"

        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player
        if (player == null) {
            left += "[WeaponSession] player=none"
            return
        }

        val localSessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        val authoritativeSessionId = WeaponRuntimeMcBridge.serverSessionIdForPlayer(player.uniqueID.toString())
        val service = WeaponRuntimeMcBridge.sessionServiceOrNull()
        if (service == null) {
            left += "[WeaponSession] bridge=not_installed"
            return
        }

        left += "[WeaponSession] sessions=${service.sessionCount()} has=${service.hasSession(localSessionId)}"
        val debugSnapshot = service.debugSnapshot(localSessionId)
        if (debugSnapshot == null) {
            left += "[WeaponSession] current=none id=$localSessionId"
            return
        }

        val snapshot = debugSnapshot.snapshot
        left += "[WeaponSession] gun=${debugSnapshot.gunId} source=${debugSnapshot.sourceId}"
        left += "[WeaponSession] state=${snapshot.state.name} ammo=${snapshot.ammoInMagazine}/${snapshot.ammoReserve}"
        left += "[WeaponSession] reloading=${snapshot.reloadTicksRemaining} cooldown=${snapshot.cooldownTicksRemaining} shots=${snapshot.totalShotsFired}"

        val synced = WeaponSessionSyncClientRegistry.get(authoritativeSessionId)
        val receipt = WeaponSessionSyncClientRegistry.receipt(authoritativeSessionId)
        if (synced == null) {
            if (receipt == null) {
                left += "[WeaponSessionSync] synced=none"
            } else {
                val receiptAgeMs = (System.currentTimeMillis() - receipt.syncedAtEpochMillis).coerceAtLeast(0L)
                left += "[WeaponSessionSync] synced=none ack=${receipt.ackSequenceId} reason=${receipt.correctionReason.name.lowercase()} ageMs=$receiptAgeMs"
            }
        } else {
            val syncedSnapshot = synced.snapshot
            val driftFields = countSnapshotDriftFields(local = snapshot, authoritative = syncedSnapshot)
            val syncAgeMs = (System.currentTimeMillis() - synced.syncedAtEpochMillis).coerceAtLeast(0L)
            val ackSequence = receipt?.ackSequenceId ?: synced.ackSequenceId
            val correctionReason = receipt?.correctionReason ?: synced.correctionReason
            left += "[WeaponSessionSync] gun=${synced.gunId} source=${synced.sourceId}"
            left += "[WeaponSessionSync] state=${syncedSnapshot.state.name} ammo=${syncedSnapshot.ammoInMagazine}/${syncedSnapshot.ammoReserve}"
            left += "[WeaponSessionSync] reload=${syncedSnapshot.reloadTicksRemaining} cooldown=${syncedSnapshot.cooldownTicksRemaining} shots=${syncedSnapshot.totalShotsFired}"
            left += "[WeaponSessionSync] driftFields=$driftFields ack=$ackSequence reason=${correctionReason.name.lowercase()} ageMs=$syncAgeMs at=${synced.syncedAtEpochMillis}"
        }

        val animationSnapshot = WeaponRuntimeMcBridge.animationSnapshotOrNull(localSessionId)
        if (animationSnapshot == null) {
            left += "[WeaponAnim] clip=none"
        } else {
            val progressPercent = String.format(Locale.ROOT, "%.0f", animationSnapshot.progress * 100f)
            left += "[WeaponAnim] clip=${animationSnapshot.clip.name} progress=${progressPercent}% elapsed=${animationSnapshot.elapsedMillis}ms dur=${animationSnapshot.durationMillis}ms"
        }

        val displayDefinition = displaySnapshot.findDefinition(debugSnapshot.gunId)
        if (displayDefinition == null) {
            left += "[GunDisplay] current=none gun=${debugSnapshot.gunId}"
        } else {
            left += "[GunDisplay] source=${displayDefinition.sourceId}"
            left += "[GunDisplay] hud=${displayDefinition.hudTexturePath ?: "none"} empty=${displayDefinition.hudEmptyTexturePath ?: "none"}"
            left += "[GunDisplay] model=${displayDefinition.modelPath ?: "none"} tex=${displayDefinition.modelTexturePath ?: "none"}"
            left += "[GunDisplay] anim=${displayDefinition.animationPath ?: "none"} sm=${displayDefinition.stateMachinePath ?: "none"}"
            left += "[GunAnimMap] idle=${displayDefinition.animationIdleClipName ?: "none"} fire=${displayDefinition.animationFireClipName ?: "none"} reload=${displayDefinition.animationReloadClipName ?: "none"} inspect=${displayDefinition.animationInspectClipName ?: "none"} dry=${displayDefinition.animationDryFireClipName ?: "none"}"
            left += "[GunAnimMap+] draw=${displayDefinition.animationDrawClipName ?: "none"} putAway=${displayDefinition.animationPutAwayClipName ?: "none"} walk=${displayDefinition.animationWalkClipName ?: "none"} run=${displayDefinition.animationRunClipName ?: "none"} aim=${displayDefinition.animationAimClipName ?: "none"} bolt=${displayDefinition.animationBoltClipName ?: "none"}"
            left += "[GunModelProbe] modelOk=${displayDefinition.modelParseSucceeded} geo=${displayDefinition.modelGeometryCount ?: -1} roots=${displayDefinition.modelRootBoneCount ?: -1} bones=${displayDefinition.modelBoneCount ?: -1} cubes=${displayDefinition.modelCubeCount ?: -1}"
            left += "[GunModelProbe] rootNames=${displayDefinition.modelRootBoneNames?.joinToString(",") ?: "none"}"
            left += "[GunModelProbe] animOk=${displayDefinition.animationParseSucceeded} clips=${displayDefinition.animationClipCount ?: -1} smOk=${displayDefinition.stateMachineResolved} pa3Ok=${displayDefinition.playerAnimatorResolved}"
        }

        val visualSample = WeaponVisualSampleRegistry.resolve(debugSnapshot.gunId, displayDefinition)
        if (visualSample == null) {
            left += "[WeaponVisual] sample=none gun=${debugSnapshot.gunId}"
            return
        }

        left += "[WeaponVisual] fp=${visualSample.firstPersonModelPath} tp=${visualSample.thirdPersonModelPath}"
        left += "[WeaponVisual] anim[idle=${visualSample.idleAnimationPath} fire=${visualSample.fireAnimationPath} reload=${visualSample.reloadAnimationPath}]"
    }

    private fun formatNsToMs(ns: Long): String {
        val ms = ns / 1_000_000.0
        return String.format("%.3fms", ms)
    }

    private fun countSnapshotDriftFields(local: WeaponSnapshot, authoritative: WeaponSnapshot): Int {
        var mismatch = 0
        if (local.state != authoritative.state) mismatch += 1
        if (local.ammoInMagazine != authoritative.ammoInMagazine) mismatch += 1
        if (local.ammoReserve != authoritative.ammoReserve) mismatch += 1
        if (local.isTriggerHeld != authoritative.isTriggerHeld) mismatch += 1
        if (local.reloadTicksRemaining != authoritative.reloadTicksRemaining) mismatch += 1
        if (local.cooldownTicksRemaining != authoritative.cooldownTicksRemaining) mismatch += 1
        if (local.semiLocked != authoritative.semiLocked) mismatch += 1
        if (local.burstShotsRemaining != authoritative.burstShotsRemaining) mismatch += 1
        if (local.totalShotsFired != authoritative.totalShotsFired) mismatch += 1
        return mismatch
    }

}

package com.tacz.legacy.client.foundation

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.ObjectAnimationRunner
import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.gui.GunRefitScreen
import com.tacz.legacy.client.gameplay.LegacyClientGunAnimationDriver
import com.tacz.legacy.client.gameplay.LegacyClientShootCoordinator
import com.tacz.legacy.client.model.BedrockGunModel
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.mixin.minecraft.client.MinecraftInvoker
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime
import com.tacz.legacy.common.foundation.FocusedSmokePlanner
import com.tacz.legacy.common.foundation.FocusedSmokeRuntime
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerAim
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerReload
import com.tacz.legacy.common.network.message.event.ServerMessageSound
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.init.Blocks
import net.minecraft.network.INetHandler
import net.minecraft.network.play.client.CPacketHeldItemChange
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameType
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import org.lwjgl.opengl.Display
import kotlin.math.abs
import kotlin.math.max

internal object FocusedSmokeClientHooks {
    private const val WORLD_LOAD_TIMEOUT_MS: Long = 45_000L
    private const val GEAR_TIMEOUT_MS: Long = 20_000L
    private const val BASELINE_DELAY_MS: Long = 2_500L
    private const val ANIMATION_WAIT_MS: Long = 8_000L
    private const val REFIT_PREVIEW_WAIT_MS: Long = 1_600L
    private const val REFIT_TOGGLE_DELAY_MS: Long = 120L
    private const val REFIT_FOCUS_DELAY_MS: Long = 350L
    private const val REFIT_INSTALL_DELAY_MS: Long = 700L
    private const val REFIT_INSTALL_VERIFY_DELAY_MS: Long = 1_050L
    private const val REFIT_LASER_PREVIEW_DELAY_MS: Long = 1_180L
    private const val ADS_READY_WAIT_MS: Long = 8_000L
    private const val ADS_READY_THRESHOLD: Float = 0.95f
    private const val RELOAD_TIMEOUT_PADDING_MS: Long = 3_000L
    private const val RELOAD_TOLERANCE_MS: Long = 250L
    private const val REGULAR_PROJECTILE_WAIT_MS: Long = 8_000L
    private const val EXPLOSION_WAIT_MS: Long = 15_000L
    private const val SHOOT_RETRY_INTERVAL_MS: Long = 750L
    private const val EXPLOSIVE_SHOT_PITCH: Float = 45.0f

    private enum class Step {
        WAIT_MENU,
        WAIT_WORLD,
        WAIT_GEAR,
        WAIT_BASELINE,
        WAIT_FIRST_RENDER,
        ATTEMPT_LEFT_CLICK_SUPPRESSION,
        ATTEMPT_REFIT,
        WAIT_REFIT,
        ATTEMPT_ADS,
        WAIT_ADS,
        ATTEMPT_INSPECT,
        WAIT_INSPECT,
        ATTEMPT_RELOAD,
        WAIT_RELOAD,
        ATTEMPT_REGULAR_SHOT,
        WAIT_REGULAR_PROJECTILE,
        SWITCH_EXPLOSIVE,
        ATTEMPT_EXPLOSIVE_SHOT,
        WAIT_EXPLOSION,
        COMPLETE,
        FAILED,
    }

    private var step: Step = Step.WAIT_MENU
    private var stepEnteredAtMs: Long = System.currentTimeMillis()
    private var worldLaunchRequested: Boolean = false
    private var worldReadyAtMs: Long = 0L
    private var animationTimeoutLogged: Boolean = false
    private var lastShootAttemptAtMs: Long = 0L
    private var originalPauseOnLostFocus: Boolean? = null
    private var keepAliveGuiActive: Boolean = false
    private var syntheticServerSoundDispatched: Boolean = false
    private var syntheticServerSoundSkipped: Boolean = false
    private var refitFocusTriggered: Boolean = false
    private var refitPropertiesToggleTriggered: Boolean = false
    private var refitInstallTriggered: Boolean = false
    private var refitInstallVerified: Boolean = false
    private var refitLaserPreviewTriggered: Boolean = false
    private var refitExpectedAttachmentId: String? = null
    private var reloadStartedAtMs: Long = 0L
    private var expectedReloadDurationMs: Long = 0L
    private var reloadGunId: String? = null

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END || !FocusedSmokeRuntime.enabled) {
            return
        }
        val mc = Minecraft.getMinecraft()
        ensurePauseOnLostFocusDisabled(mc)
        if (FocusedSmokeRuntime.hasFailed()) {
            FocusedSmokeRuntime.setForcedAimActive(false)
            step = Step.FAILED
            restorePauseOnLostFocus(mc)
            return
        }
        if (FocusedSmokeRuntime.hasPassed()) {
            FocusedSmokeRuntime.setForcedAimActive(false)
            step = Step.COMPLETE
            restorePauseOnLostFocus(mc)
            return
        }
        val world = mc.world
        val player = mc.player
        if (world == null || player == null) {
            handleOutOfWorld(mc)
            return
        }

        if (worldReadyAtMs == 0L) {
            worldReadyAtMs = System.currentTimeMillis()
            mc.gameSettings.thirdPersonView = 0
            transition(Step.WAIT_GEAR, "CLIENT_WORLD_READY dimension=${world.provider.dimension} player=${player.name}")
        }

        maintainClientLiveness(mc)

        when (step) {
            Step.WAIT_GEAR -> handleWaitGear(player)
            Step.WAIT_BASELINE -> handleWaitBaseline(player)
            Step.WAIT_FIRST_RENDER -> handleWaitFirstRender(player)
            Step.ATTEMPT_LEFT_CLICK_SUPPRESSION -> handleAttemptLeftClickSuppression(player)
            Step.ATTEMPT_REFIT -> handleAttemptRefit(player)
            Step.WAIT_REFIT -> handleWaitRefit(player)
            Step.ATTEMPT_ADS -> handleAttemptAds(player)
            Step.WAIT_ADS -> handleWaitAds(player)
            Step.ATTEMPT_INSPECT -> handleAttemptInspect(player)
            Step.WAIT_INSPECT -> handleWaitInspect()
            Step.ATTEMPT_RELOAD -> handleAttemptReload(player)
            Step.WAIT_RELOAD -> handleWaitReload(player)
            Step.ATTEMPT_REGULAR_SHOT -> handleAttemptRegularShot(player)
            Step.WAIT_REGULAR_PROJECTILE -> handleWaitRegularProjectile()
            Step.SWITCH_EXPLOSIVE -> handleSwitchExplosive(player)
            Step.ATTEMPT_EXPLOSIVE_SHOT -> handleAttemptExplosiveShot(player)
            Step.WAIT_EXPLOSION -> handleWaitExplosion()
            Step.COMPLETE,
            Step.FAILED,
            Step.WAIT_MENU,
            Step.WAIT_WORLD -> Unit
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    fun onRenderHand(event: RenderSpecificHandEvent) {
        if (!FocusedSmokeRuntime.enabled || event.hand != EnumHand.MAIN_HAND) {
            return
        }
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val stack = event.itemStack
        val iGun = stack.item as? IGun ?: return
        val gunId = iGun.getGunId(stack)
        val plan = FocusedSmokeRuntime.currentPlan() ?: return
        if (gunId != plan.regularGunId && gunId != plan.explosiveGunId) {
            return
        }
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displaySummary = FocusedSmokePlanner.describeGunDisplay(snapshot, gunId)
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId)
        val displayInstance = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)
        val gunModel = displayInstance?.gunModel
        val stateMachine = displayInstance?.animationStateMachine
        val stateNames = reflectStateNames(stateMachine)
        val runnerSummary = reflectRunnerSummary(stateMachine?.animationController)
        val attachmentSummary = collectAttachmentSummary(iGun, stack)
        val aimingPathSummary = collectAimingPathSummary(gunModel, stack)
        val soundSummary = displaySummary?.soundKeys?.joinToString(",")?.ifBlank { "none" } ?: "none"
        val details = buildString {
            append("gun=").append(gunId)
            append(" display=").append(displayId ?: "none")
            append(" runtime=").append(gunModel?.javaClass?.simpleName ?: "none")
            append(" stateMachine=").append(displaySummary?.stateMachineId ?: "none")
            append(" defaultType=").append(displaySummary?.defaultAnimationType ?: "none")
            append(" defaultAnimation=").append(displaySummary?.defaultAnimationId ?: "none")
            append(" sounds=").append(soundSummary)
            append(" smInitialized=").append(stateMachine?.isInitialized ?: false)
            append(" states=").append(stateNames)
            append(" runners=").append(runnerSummary)
            append(" attachments=").append(attachmentSummary)
            append(" aimingPath=").append(aimingPathSummary)
            append(" player=").append(player.name)
        }
        FocusedSmokeRuntime.markAnimationObserved(details)
        maybeDispatchSyntheticServerSound(player, gunId, displayId, displaySummary?.soundKeys.orEmpty())
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        if (!FocusedSmokeRuntime.enabled || !event.world.isRemote) {
            return
        }
        resetClientState()
        FocusedSmokeRuntime.resetScenarioState()
    }

    private fun handleOutOfWorld(mc: Minecraft) {
        worldReadyAtMs = 0L
        when (step) {
            Step.COMPLETE,
            Step.FAILED -> {
                restorePauseOnLostFocus(mc)
                return
            }
            else -> Unit
        }
        if (!FocusedSmokeRuntime.autoWorldLaunchEnabled) {
            if (step == Step.WAIT_MENU) {
                transition(Step.WAIT_WORLD, "AUTO_WORLD_LAUNCH_SKIPPED autoWorld=false")
            }
            return
        }
        if (!worldLaunchRequested && mc.currentScreen is GuiMainMenu) {
            val settings = WorldSettings(
                System.currentTimeMillis(),
                GameType.CREATIVE,
                true,
                false,
                WorldType.FLAT,
            ).enableCommands()
            worldLaunchRequested = true
            transition(
                Step.WAIT_WORLD,
                "AUTO_WORLD_LAUNCH folder=${FocusedSmokeRuntime.worldFolderName} world=${FocusedSmokeRuntime.worldDisplayName}"
            )
            mc.launchIntegratedServer(FocusedSmokeRuntime.worldFolderName, FocusedSmokeRuntime.worldDisplayName, settings)
            return
        }
        if (step == Step.WAIT_WORLD && elapsedMs() > WORLD_LOAD_TIMEOUT_MS) {
            FocusedSmokeRuntime.markFailure("world_load_timeout")
            step = Step.FAILED
        }
    }

    private fun handleWaitGear(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        if (FocusedSmokeRuntime.serverGearReady && holdsGun(player, plan.regularGunId)) {
            transition(
                Step.WAIT_BASELINE,
                "CLIENT_GEAR_READY regularGun=${plan.regularGunId} explosiveGun=${plan.explosiveGunId ?: "none"}"
            )
            return
        }
        if (elapsedMs() > GEAR_TIMEOUT_MS) {
            FocusedSmokeRuntime.markFailure("gear_not_prepared")
            step = Step.FAILED
        }
    }

    private fun handleWaitBaseline(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        if (elapsedMs() < BASELINE_DELAY_MS) {
            return
        }
        val operator = IGunOperator.fromLivingEntity(player)
        transition(
            Step.WAIT_FIRST_RENDER,
            "BASELINE_READY baseTimestamp=${operator.getDataHolder().baseTimestamp} drawCooldown=${operator.getSynDrawCoolDown()}"
        )
    }

    private fun handleWaitFirstRender(player: EntityPlayerSP) {
        if (FocusedSmokeRuntime.animationObserved) {
            transition(nextPostAnimationStep(), "ANIMATION_GATE_OPEN")
            return
        }
        if (!animationTimeoutLogged && elapsedMs() > ANIMATION_WAIT_MS) {
            animationTimeoutLogged = true
            FocusedSmokeRuntime.markRegularShootResult("ANIMATION_PENDING_TIMEOUT", currentGunId(player) ?: return)
            transition(nextPostAnimationStep(), "ANIMATION_WAIT_TIMEOUT continue_to_inspect_check=true")
        }
    }

    private fun handleAttemptLeftClickSuppression(player: EntityPlayerSP) {
        val mc = Minecraft.getMinecraft()
        val probePos = findLeftClickProbeBlock(player) ?: run {
            FocusedSmokeRuntime.markFailure("left_click_probe_block_missing")
            step = Step.FAILED
            return
        }
        val originalState = player.world.getBlockState(probePos)
        val insertedTemporaryBlock = player.world.isAirBlock(probePos)
        if (insertedTemporaryBlock && !player.world.setBlockState(probePos, Blocks.STONE.defaultState)) {
            FocusedSmokeRuntime.markFailure("left_click_probe_block_place_failed")
            step = Step.FAILED
            return
        }
        val probeState = player.world.getBlockState(probePos)

        val minecraftInvoker = mc as? MinecraftInvoker
        if (minecraftInvoker == null) {
            if (insertedTemporaryBlock) {
                player.world.setBlockState(probePos, originalState)
            }
            FocusedSmokeRuntime.markFailure("left_click_probe_invoke_failed")
            step = Step.FAILED
            return
        }

        val originalHit = mc.objectMouseOver
        val originalSwing = player.isSwingInProgress
        val originalLeftClickCounter = minecraftInvoker.`tacz$getLeftClickCounter`()

        mc.objectMouseOver = RayTraceResult(Vec3d(probePos).add(0.5, 0.5, 0.5), EnumFacing.UP, probePos)
        minecraftInvoker.`tacz$setLeftClickCounter`(0)

        val invokeSucceeded = runCatching {
            minecraftInvoker.`tacz$invokeClickMouse`()
            minecraftInvoker.`tacz$invokeSendClickBlockToController`(true)
        }.isSuccess

        mc.objectMouseOver = originalHit
        minecraftInvoker.`tacz$setLeftClickCounter`(originalLeftClickCounter)
        mc.playerController?.resetBlockRemoving()

        val stateAfter = player.world.getBlockState(probePos)
        if (insertedTemporaryBlock) {
            player.world.setBlockState(probePos, originalState)
        }

        if (!invokeSucceeded) {
            FocusedSmokeRuntime.markFailure("left_click_probe_invoke_failed")
            step = Step.FAILED
            return
        }

        val blockChanged = stateAfter != probeState
        val hittingBlock = mc.playerController?.getIsHittingBlock() == true
        val swingStarted = player.isSwingInProgress && !originalSwing

        if (blockChanged || hittingBlock || swingStarted) {
            FocusedSmokeRuntime.markFailure(
                buildString {
                    append("left_click_not_suppressed")
                    append("_blockChanged=").append(blockChanged)
                    append("_hittingBlock=").append(hittingBlock)
                    append("_swing=").append(swingStarted)
                },
            )
            step = Step.FAILED
            return
        }

        FocusedSmokeRuntime.markLeftClickSuppressed(
            "target=$probePos block=${probeState.block.registryName} swing=$swingStarted hittingBlock=$hittingBlock temporary=$insertedTemporaryBlock",
        )
        transition(nextPostSuppressionStep(), "LEFT_CLICK_SUPPRESSED target=$probePos")
    }

    private fun handleAttemptRefit(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        val stack = player.heldItemMainhand
        if (!holdsGun(player, plan.regularGunId)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("regular_gun_lost_before_refit")
                step = Step.FAILED
            }
            return
        }
        if (!LegacyGunRefitRuntime.canOpenRefit(stack)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("refit_screen_unavailable")
                step = Step.FAILED
            }
            return
        }
        val mc = Minecraft.getMinecraft()
        if (mc.currentScreen !is GunRefitScreen) {
            mc.displayGuiScreen(GunRefitScreen())
        }
        transition(Step.WAIT_REFIT, "REFIT_SCREEN_OPEN gun=${plan.regularGunId}")
    }

    private fun handleWaitRefit(player: EntityPlayerSP) {
        val mc = Minecraft.getMinecraft()
        val refitScreen = mc.currentScreen as? GunRefitScreen
        if (refitScreen == null) {
            if (elapsedMs() > REFIT_PREVIEW_WAIT_MS) {
                FocusedSmokeRuntime.markFailure("refit_screen_unavailable")
                step = Step.FAILED
            }
            return
        }
        if (!refitPropertiesToggleTriggered && elapsedMs() >= REFIT_TOGGLE_DELAY_MS) {
            refitPropertiesToggleTriggered = true
            val hidden = refitScreen.triggerFocusedSmokeToggleProperties()
            TACZLegacy.logger.info("[FocusedSmoke] REFIT_PROPERTIES_TOGGLE hidden={}", hidden)
            val restored = refitScreen.triggerFocusedSmokeToggleProperties()
            TACZLegacy.logger.info("[FocusedSmoke] REFIT_PROPERTIES_TOGGLE hidden={}", restored)
        }
        if (!refitFocusTriggered && elapsedMs() >= REFIT_FOCUS_DELAY_MS) {
            refitFocusTriggered = true
            val selectedType = refitScreen.triggerFocusedSmokeSelectType()
            if (selectedType == null) {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] REFIT_SLOT_FOCUS_SKIPPED gun={} reason=no_available_slot",
                    currentGunId(player),
                )
            } else {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] REFIT_SLOT_FOCUS gun={} type={}",
                    currentGunId(player),
                    selectedType.serializedName,
                )
            }
        }
        if (!refitInstallTriggered && elapsedMs() >= REFIT_INSTALL_DELAY_MS) {
            refitInstallTriggered = true
            refitExpectedAttachmentId = refitScreen.triggerFocusedSmokeInstallFirstCandidate()?.toString()
            if (refitExpectedAttachmentId == null) {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] REFIT_ATTACHMENT_CLICK_SKIPPED gun={} reason=no_candidate",
                    currentGunId(player),
                )
            } else {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] REFIT_ATTACHMENT_CLICK gun={} attachment={}",
                    currentGunId(player),
                    refitExpectedAttachmentId,
                )
            }
        }
        if (!refitInstallVerified && refitExpectedAttachmentId != null && elapsedMs() >= REFIT_INSTALL_VERIFY_DELAY_MS) {
            val installedId = refitScreen.focusedSmokeSelectedAttachmentId()?.toString()
            if (installedId == refitExpectedAttachmentId) {
                refitInstallVerified = true
                TACZLegacy.logger.info(
                    "[FocusedSmoke] REFIT_ATTACHMENT_APPLIED gun={} attachment={}",
                    currentGunId(player),
                    installedId,
                )
            }
        }
        if (!refitLaserPreviewTriggered && elapsedMs() >= REFIT_LASER_PREVIEW_DELAY_MS) {
            refitLaserPreviewTriggered = true
            val previewColor = refitScreen.triggerFocusedSmokeAdjustLaserPreview()
            if (previewColor == null) {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] LASER_COLOR_PREVIEW_SKIPPED gun={} attachment={} reason=no_editable_laser",
                    currentGunId(player),
                    refitScreen.focusedSmokeSelectedAttachmentId() ?: "gun",
                )
            } else {
                TACZLegacy.logger.info(
                    "[FocusedSmoke] LASER_COLOR_PREVIEW gun={} attachment={} color=0x{}",
                    currentGunId(player),
                    refitScreen.focusedSmokeSelectedAttachmentId() ?: "gun",
                    String.format("%06X", previewColor and 0xFFFFFF),
                )
            }
        }
        if (elapsedMs() >= REFIT_PREVIEW_WAIT_MS) {
            if (refitExpectedAttachmentId != null && !refitInstallVerified) {
                FocusedSmokeRuntime.markFailure("refit_attachment_not_applied")
                step = Step.FAILED
                return
            }
            mc.displayGuiScreen(null)
            if (FocusedSmokeRuntime.passAfterRefitEnabled && refitExpectedAttachmentId != null && refitInstallVerified) {
                FocusedSmokeRuntime.forcePass("refit_preview")
                transition(Step.COMPLETE, "REFIT_PREVIEW_COMPLETE gun=${currentGunId(player)}")
                return
            }
            transition(nextPostRefitStep(), "REFIT_PREVIEW_COMPLETE gun=${currentGunId(player)}")
        }
    }

    private fun handleAttemptInspect(player: EntityPlayerSP) {
        if (FocusedSmokeRuntime.skipInspectEnabled) {
            transition(nextPostInspectStep(), "INSPECT_SKIPPED enabled=true")
            return
        }
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        if (!holdsGun(player, plan.regularGunId)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("regular_gun_lost_before_inspect")
                step = Step.FAILED
            }
            return
        }
        val stack = player.heldItemMainhand
        LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_INSPECT)
        transition(Step.WAIT_INSPECT, "INSPECT_TRIGGERED gun=${plan.regularGunId}")
    }

    private fun handleAttemptAds(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        val operator = IGunOperator.fromLivingEntity(player)
        if (!holdsGun(player, plan.regularGunId) || !canAttemptAim(operator)) {
            if (elapsedMs() > ADS_READY_WAIT_MS) {
                FocusedSmokeRuntime.markFailure("ads_not_ready")
                step = Step.FAILED
            }
            return
        }
        player.isSprinting = false
        FocusedSmokeRuntime.setForcedAimActive(true)
        setAimState(operator, true)
        transition(Step.WAIT_ADS, "ADS_TRIGGERED gun=${plan.regularGunId}")
    }

    private fun handleWaitAds(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        val stack = player.heldItemMainhand
        val operator = IGunOperator.fromLivingEntity(player)
        if (!holdsGun(player, plan.regularGunId)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("regular_gun_lost_before_ads")
                step = Step.FAILED
            }
            return
        }
        player.isSprinting = false
        FocusedSmokeRuntime.setForcedAimActive(true)
        if (!operator.getSynIsAiming()) {
            setAimState(operator, true)
        }
        val aimingProgress = operator.getSynAimingProgress().coerceIn(0.0f, 1.0f)
        if (aimingProgress >= ADS_READY_THRESHOLD) {
            val gunModel = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)?.gunModel
            val nextStep = if (FocusedSmokeRuntime.passAfterAimEnabled) Step.COMPLETE else nextPostAimStep()
            val marker = buildString {
                append("ADS_READY gun=").append(plan.regularGunId)
                append(" aimingProgress=").append(String.format("%.3f", aimingProgress))
                append(" attachments=").append(collectAttachmentSummary(stack.item as IGun, stack))
                append(" aimingPath=").append(collectAimingPathSummary(gunModel, stack))
            }
            if (FocusedSmokeRuntime.passAfterAimEnabled) {
                FocusedSmokeRuntime.forcePass("ads_only")
            }
            transition(nextStep, marker)
            return
        }
        if (elapsedMs() > ADS_READY_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("ads_timeout")
            step = Step.FAILED
        }
    }

    private fun handleWaitInspect() {
        if (elapsedMs() > 4000L) {
            transition(
                nextPostInspectStep(),
                "INSPECT_COMPLETED wait=4000ms skipReload=${FocusedSmokeRuntime.skipReloadEnabled}",
            )
        }
    }

    private fun handleAttemptReload(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        if (!holdsGun(player, plan.regularGunId)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("reload_gun_missing")
                step = Step.FAILED
            }
            return
        }
        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: run {
            FocusedSmokeRuntime.markFailure("reload_item_not_gun")
            step = Step.FAILED
            return
        }
        val operator = IGunOperator.fromLivingEntity(player)
        if (operator.getSynDrawCoolDown() != 0L || operator.getSynReloadState().stateType.isReloading() || operator.getSynIsBolting()) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("reload_not_ready")
                step = Step.FAILED
            }
            return
        }
        val gunData = GunDataAccessor.getGunData(iGun.getGunId(stack)) ?: run {
            FocusedSmokeRuntime.markFailure("reload_gun_data_missing")
            step = Step.FAILED
            return
        }
        val currentAmmo = iGun.getCurrentAmmoCount(stack)
        val emptyReload = currentAmmo <= 0 && !iGun.hasBulletInBarrel(stack)
        val expectedMs = if (emptyReload) {
            ((gunData.emptyReloadFeedingTimeS + gunData.emptyReloadFinishingTimeS) * 1000.0).toLong()
        } else {
            ((gunData.reloadFeedingTimeS + gunData.reloadFinishingTimeS) * 1000.0).toLong()
        }
        val before = operator.getSynReloadState().stateType
        operator.reload()
        if (before == operator.getSynReloadState().stateType) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("reload_not_started")
                step = Step.FAILED
            }
            return
        }
        TACZNetworkHandler.sendToServer(ClientMessagePlayerReload())
        LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_RELOAD)
        reloadStartedAtMs = System.currentTimeMillis()
        expectedReloadDurationMs = expectedMs
        reloadGunId = iGun.getGunId(stack).toString()
        transition(
            Step.WAIT_RELOAD,
            "RELOAD_STARTED gun=${reloadGunId} state=${operator.getSynReloadState().stateType} expectedMs=$expectedReloadDurationMs empty=$emptyReload currentAmmo=$currentAmmo"
        )
    }

    private fun handleWaitReload(player: EntityPlayerSP) {
        val operator = IGunOperator.fromLivingEntity(player)
        if (!operator.getSynReloadState().stateType.isReloading()) {
            val actualMs = max(0L, System.currentTimeMillis() - reloadStartedAtMs)
            val deltaMs = actualMs - expectedReloadDurationMs
            TACZLegacy.logger.info(
                "[FocusedSmoke] RELOAD_COMPLETED gun={} expectedMs={} actualMs={} deltaMs={}",
                reloadGunId ?: currentGunId(player),
                expectedReloadDurationMs,
                actualMs,
                deltaMs,
            )
            if (abs(deltaMs) > RELOAD_TOLERANCE_MS) {
                FocusedSmokeRuntime.markFailure("reload_timing_delta_${deltaMs}")
                step = Step.FAILED
                return
            }
            transition(Step.ATTEMPT_REGULAR_SHOT, "RELOAD_TIMING_OK deltaMs=$deltaMs")
            return
        }
        if (elapsedMs() > expectedReloadDurationMs + RELOAD_TIMEOUT_PADDING_MS) {
            FocusedSmokeRuntime.markFailure("reload_timeout")
            step = Step.FAILED
        }
    }

    private fun handleAttemptRegularShot(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        val operator = IGunOperator.fromLivingEntity(player)
        if (!holdsGun(player, plan.regularGunId) || !canAttemptShoot(operator)) {
            if (elapsedMs() > REGULAR_PROJECTILE_WAIT_MS) {
                FocusedSmokeRuntime.markFailure("regular_shot_not_ready")
                step = Step.FAILED
            }
            return
        }
        applyRegularShotViewOverride(player)
        if (System.currentTimeMillis() - lastShootAttemptAtMs < SHOOT_RETRY_INTERVAL_MS) {
            return
        }
        player.isSprinting = false
        lastShootAttemptAtMs = System.currentTimeMillis()
        val result = attemptSmokeShoot(
            player,
            operator,
            pitch = FocusedSmokeRuntime.regularShotPitchOverride ?: player.rotationPitch,
            yaw = FocusedSmokeRuntime.regularShotYawOverride ?: player.rotationYaw,
        )
        FocusedSmokeRuntime.markRegularShootResult(result.name, plan.regularGunId)
        if (result == ShootResult.SUCCESS) {
            transition(Step.WAIT_REGULAR_PROJECTILE, "REGULAR_SHOT_SENT gun=${plan.regularGunId}")
        } else if (elapsedMs() > REGULAR_PROJECTILE_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("regular_shot_failed_${result.name.lowercase()}")
            step = Step.FAILED
        }
    }

    private fun applyRegularShotViewOverride(player: EntityPlayerSP) {
        val pitchOverride = FocusedSmokeRuntime.regularShotPitchOverride
        val yawOverride = FocusedSmokeRuntime.regularShotYawOverride
        if (pitchOverride == null && yawOverride == null) {
            return
        }
        pitchOverride?.let { pitch ->
            player.prevRotationPitch = pitch
            player.rotationPitch = pitch
            player.prevRenderArmPitch = pitch
            player.renderArmPitch = pitch
        }
        yawOverride?.let { yaw ->
            player.prevRotationYaw = yaw
            player.rotationYaw = yaw
            player.prevRotationYawHead = yaw
            player.rotationYawHead = yaw
            player.prevRenderArmYaw = yaw
            player.renderArmYaw = yaw
        }
    }

    private fun handleWaitRegularProjectile() {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        if (FocusedSmokeRuntime.regularProjectileObserved && FocusedSmokeRuntime.hasObservedExpectedRegularFireCount()) {
            if (plan.explosiveGunId == null) {
                finalizeRun()
            } else {
                transition(
                    Step.SWITCH_EXPLOSIVE,
                    "REGULAR_PROJECTILE_GATE_OPEN gun=${plan.regularGunId} fireCount=${FocusedSmokeRuntime.regularGunFireCount}/${FocusedSmokeRuntime.expectedRegularFireCount()}"
                )
            }
            return
        }
        if (elapsedMs() > REGULAR_PROJECTILE_WAIT_MS) {
            val reason = if (!FocusedSmokeRuntime.regularProjectileObserved) {
                "regular_projectile_missing"
            } else {
                "regular_burst_incomplete_${FocusedSmokeRuntime.regularGunFireCount}_of_${FocusedSmokeRuntime.expectedRegularFireCount()}"
            }
            FocusedSmokeRuntime.markFailure(reason)
            step = Step.FAILED
        }
    }

    private fun handleSwitchExplosive(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        val explosiveGunId = plan.explosiveGunId ?: run {
            finalizeRun()
            return
        }
        ensureHoldingGun(player, explosiveGunId, preferredSlot = 1)
        if (!holdsGun(player, explosiveGunId)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("explosive_gun_not_prepared")
                step = Step.FAILED
            }
            return
        }
        if (elapsedMs() >= BASELINE_DELAY_MS) {
            transition(Step.ATTEMPT_EXPLOSIVE_SHOT, "EXPLOSIVE_GUN_READY gun=$explosiveGunId")
        }
    }

    private fun handleAttemptExplosiveShot(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        val explosiveGunId = plan.explosiveGunId ?: run {
            finalizeRun()
            return
        }
        ensureHoldingGun(player, explosiveGunId, preferredSlot = 1)
        val operator = IGunOperator.fromLivingEntity(player)
        if (!holdsGun(player, explosiveGunId) || !canAttemptShoot(operator)) {
            if (elapsedMs() > EXPLOSION_WAIT_MS) {
                FocusedSmokeRuntime.markFailure("explosive_shot_not_ready")
                step = Step.FAILED
            }
            return
        }
        if (System.currentTimeMillis() - lastShootAttemptAtMs < SHOOT_RETRY_INTERVAL_MS) {
            return
        }
        player.isSprinting = false
        lastShootAttemptAtMs = System.currentTimeMillis()
        val result = attemptSmokeShoot(player, operator, pitch = EXPLOSIVE_SHOT_PITCH)
        FocusedSmokeRuntime.markExplosiveShootResult(result.name, explosiveGunId)
        if (result == ShootResult.SUCCESS) {
            transition(Step.WAIT_EXPLOSION, "EXPLOSIVE_SHOT_SENT gun=$explosiveGunId")
        } else if (elapsedMs() > EXPLOSION_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("explosive_shot_failed_${result.name.lowercase()}")
            step = Step.FAILED
        }
    }

    private fun handleWaitExplosion() {
        if (FocusedSmokeRuntime.explosionObserved) {
            finalizeRun()
            return
        }
        if (elapsedMs() > EXPLOSION_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("explosion_missing")
            step = Step.FAILED
        }
    }

    private fun finalizeRun() {
        clearKeepAliveGuiIfNeeded(Minecraft.getMinecraft())
        if (FocusedSmokeRuntime.hasPassed()) {
            step = Step.COMPLETE
            return
        }
        val plan = FocusedSmokeRuntime.currentPlan()
        val animationReady = FocusedSmokeRuntime.animationObserved
        val projectileReady = FocusedSmokeRuntime.regularProjectileObserved
        val explosionReady = plan?.explosiveGunId == null || FocusedSmokeRuntime.explosionObserved
        if (animationReady && projectileReady && explosionReady) {
            step = Step.COMPLETE
            return
        }
        val reason = when {
            !animationReady -> "animation_missing"
            !projectileReady -> "regular_projectile_missing"
            !explosionReady -> "explosion_missing"
            else -> "focused_smoke_incomplete"
        }
        FocusedSmokeRuntime.markFailure(reason)
        step = Step.FAILED
    }

    private fun canAttemptShoot(operator: IGunOperator): Boolean {
        if (operator.getSynDrawCoolDown() != 0L) {
            return false
        }
        if (operator.getSynReloadState().stateType.isReloading()) {
            return false
        }
        if (operator.getSynIsBolting()) {
            return false
        }
        return true
    }

    private fun canAttemptAim(operator: IGunOperator): Boolean {
        if (operator.getSynDrawCoolDown() != 0L) {
            return false
        }
        if (operator.getSynReloadState().stateType.isReloading()) {
            return false
        }
        if (operator.getSynIsBolting()) {
            return false
        }
        return true
    }

    private fun attemptSmokeShoot(
        player: EntityPlayerSP,
        operator: IGunOperator,
        pitch: Float = player.rotationPitch,
        yaw: Float = player.rotationYaw,
    ): ShootResult {
        return LegacyClientShootCoordinator.attemptShoot(player, operator, pitch, yaw, triggerAnimation = true)
    }

    private fun ensureHoldingGun(player: EntityPlayerSP, gunId: net.minecraft.util.ResourceLocation, preferredSlot: Int) {
        if (holdsGun(player, gunId)) {
            return
        }
        if (preferredSlot in 0..8 && stackGunId(player.inventory.getStackInSlot(preferredSlot)) == gunId) {
            switchHotbarSlot(player, preferredSlot)
            return
        }
        for (slot in 0..8) {
            if (stackGunId(player.inventory.getStackInSlot(slot)) == gunId) {
                switchHotbarSlot(player, slot)
                return
            }
        }
    }

    private fun switchHotbarSlot(player: EntityPlayerSP, slot: Int) {
        if (slot !in 0..8 || player.inventory.currentItem == slot) {
            return
        }
        player.inventory.currentItem = slot
        player.connection.sendPacket(CPacketHeldItemChange(slot))
    }

    private fun holdsGun(player: EntityPlayerSP, gunId: net.minecraft.util.ResourceLocation): Boolean =
        stackGunId(player.heldItemMainhand) == gunId

    private fun currentGunId(player: EntityPlayerSP): net.minecraft.util.ResourceLocation? = stackGunId(player.heldItemMainhand)

    private fun stackGunId(stack: net.minecraft.item.ItemStack): net.minecraft.util.ResourceLocation? =
        (stack.item as? IGun)?.getGunId(stack)

    private fun transition(next: Step, marker: String) {
        FocusedSmokeRuntime.setForcedAimActive(shouldKeepAutoAim(next))
        step = next
        stepEnteredAtMs = System.currentTimeMillis()
        lastShootAttemptAtMs = 0L
        com.tacz.legacy.TACZLegacy.logger.info("[FocusedSmoke] {}", marker)
    }

    private fun elapsedMs(): Long = max(0L, System.currentTimeMillis() - stepEnteredAtMs)

    private fun resetClientState() {
        step = Step.WAIT_MENU
        stepEnteredAtMs = System.currentTimeMillis()
        worldLaunchRequested = false
        worldReadyAtMs = 0L
        animationTimeoutLogged = false
        lastShootAttemptAtMs = 0L
        originalPauseOnLostFocus = null
        keepAliveGuiActive = false
        syntheticServerSoundDispatched = false
        syntheticServerSoundSkipped = false
        refitFocusTriggered = false
        refitPropertiesToggleTriggered = false
        refitInstallTriggered = false
        refitInstallVerified = false
        refitLaserPreviewTriggered = false
        refitExpectedAttachmentId = null
        reloadStartedAtMs = 0L
        expectedReloadDurationMs = 0L
        reloadGunId = null
        FocusedSmokeRuntime.setForcedAimActive(false)
    }

    private fun maintainClientLiveness(mc: Minecraft) {
        ensurePauseOnLostFocusDisabled(mc)
        updateKeepAliveGui(mc)
    }

    private fun ensurePauseOnLostFocusDisabled(mc: Minecraft) {
        if (originalPauseOnLostFocus == null) {
            originalPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus
        }
        if (mc.gameSettings.pauseOnLostFocus) {
            mc.gameSettings.pauseOnLostFocus = false
            com.tacz.legacy.TACZLegacy.logger.info("[FocusedSmoke] pauseOnLostFocus=false")
        }
    }

    private fun restorePauseOnLostFocus(mc: Minecraft) {
        clearKeepAliveGuiIfNeeded(mc)
        val original = originalPauseOnLostFocus ?: return
        mc.gameSettings.pauseOnLostFocus = original
        originalPauseOnLostFocus = null
    }

    private fun updateKeepAliveGui(mc: Minecraft) {
        val wantsKeepAliveGui = when (step) {
            Step.WAIT_GEAR,
            Step.WAIT_BASELINE,
            Step.WAIT_FIRST_RENDER,
            Step.ATTEMPT_LEFT_CLICK_SUPPRESSION,
            Step.ATTEMPT_REFIT,
            Step.WAIT_REFIT,
            Step.ATTEMPT_INSPECT,
            Step.WAIT_INSPECT,
            Step.ATTEMPT_RELOAD,
            Step.WAIT_RELOAD,
            Step.ATTEMPT_REGULAR_SHOT,
            Step.WAIT_REGULAR_PROJECTILE,
            Step.SWITCH_EXPLOSIVE,
            Step.ATTEMPT_EXPLOSIVE_SHOT,
            Step.WAIT_EXPLOSION -> true
            else -> false
        }
        val isFocusAtRisk = !Display.isActive() || !mc.inGameHasFocus
        val shouldOpenChat = wantsKeepAliveGui && isFocusAtRisk
        if (shouldOpenChat) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(GuiChat(""))
                keepAliveGuiActive = true
                com.tacz.legacy.TACZLegacy.logger.info(
                    "[FocusedSmoke] KEEPALIVE_GUI_OPEN screen=GuiChat displayActive={} inGameHasFocus={}",
                    Display.isActive(),
                    mc.inGameHasFocus,
                )
            }
            return
        }
        if (keepAliveGuiActive && (mc.currentScreen is GuiChat) && (!wantsKeepAliveGui || !isFocusAtRisk)) {
            clearKeepAliveGuiIfNeeded(mc)
        }
    }

    private fun nextPostAnimationStep(): Step = Step.ATTEMPT_LEFT_CLICK_SUPPRESSION

    private fun nextPostSuppressionStep(): Step = when {
        FocusedSmokeRuntime.refitPreviewEnabled -> Step.ATTEMPT_REFIT
        FocusedSmokeRuntime.autoAimEnabled -> Step.ATTEMPT_ADS
        FocusedSmokeRuntime.skipInspectEnabled -> nextPostInspectStep()
        else -> Step.ATTEMPT_INSPECT
    }

    private fun nextPostRefitStep(): Step =
        when {
            FocusedSmokeRuntime.autoAimEnabled -> Step.ATTEMPT_ADS
            FocusedSmokeRuntime.skipInspectEnabled -> nextPostInspectStep()
            else -> Step.ATTEMPT_INSPECT
        }

    private fun nextPostAimStep(): Step =
        if (FocusedSmokeRuntime.skipInspectEnabled) nextPostInspectStep() else Step.ATTEMPT_INSPECT

    private fun nextPostInspectStep(): Step =
        if (FocusedSmokeRuntime.skipReloadEnabled) Step.ATTEMPT_REGULAR_SHOT else Step.ATTEMPT_RELOAD

    private fun shouldKeepAutoAim(step: Step): Boolean {
        if (!FocusedSmokeRuntime.autoAimEnabled) {
            return false
        }
        return when (step) {
            Step.ATTEMPT_ADS,
            Step.WAIT_ADS,
            Step.ATTEMPT_REGULAR_SHOT,
            Step.WAIT_REGULAR_PROJECTILE -> true
            else -> false
        }
    }

    private fun setAimState(operator: IGunOperator, aiming: Boolean) {
        if (operator.getSynIsAiming() == aiming) {
            return
        }
        operator.aim(aiming)
        TACZNetworkHandler.sendToServer(ClientMessagePlayerAim(aiming))
    }

    private fun findLeftClickProbeBlock(player: EntityPlayerSP): BlockPos? {
        val directHit = Minecraft.getMinecraft().objectMouseOver
        if (directHit?.typeOfHit == RayTraceResult.Type.BLOCK && directHit.blockPos != null && !player.world.isAirBlock(directHit.blockPos)) {
            return directHit.blockPos
        }

        val base = BlockPos(player.posX, player.posY, player.posZ)
        val candidates = mutableListOf<BlockPos>()
        for (radius in 0..4) {
            for (dy in -3..1) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (radius > 0 && kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) {
                            continue
                        }
                        candidates += base.add(dx, dy, dz)
                    }
                }
            }
        }
        return candidates.firstOrNull { !player.world.isAirBlock(it) } ?: base.north(2)
    }

    private fun clearKeepAliveGuiIfNeeded(mc: Minecraft) {
        if (!keepAliveGuiActive) {
            return
        }
        if (mc.currentScreen is GuiChat) {
            mc.displayGuiScreen(null)
            com.tacz.legacy.TACZLegacy.logger.info("[FocusedSmoke] KEEPALIVE_GUI_CLOSE")
        }
        keepAliveGuiActive = false
    }

    private fun maybeDispatchSyntheticServerSound(
        player: EntityPlayerSP,
        gunId: net.minecraft.util.ResourceLocation,
        displayId: net.minecraft.util.ResourceLocation?,
        soundKeys: List<String>,
    ) {
        if (syntheticServerSoundDispatched || syntheticServerSoundSkipped) {
            return
        }
        val actualDisplayId = displayId ?: return
        val soundKey = pickSyntheticServerSoundKey(soundKeys) ?: return
        val messageContext = createSyntheticClientMessageContext()
        if (messageContext == null) {
            syntheticServerSoundSkipped = true
            TACZLegacy.logger.info("[FocusedSmoke][Audio] SERVER_MESSAGE_SYNTHETIC_SKIPPED reason=context_unavailable")
            return
        }
        syntheticServerSoundDispatched = true
        ServerMessageSound.Handler().onMessage(
            ServerMessageSound(
                player.entityId,
                gunId,
                actualDisplayId,
                soundKey,
                0.15f,
                1.0f,
                8,
            ),
            messageContext,
        )
        TACZLegacy.logger.info(
            "[FocusedSmoke][Audio] SERVER_MESSAGE_SYNTHETIC_SENT gun={} display={} soundKey={}",
            gunId,
            actualDisplayId,
            soundKey,
        )
    }

    private fun pickSyntheticServerSoundKey(soundKeys: List<String>): String? {
        if (soundKeys.isEmpty()) {
            return null
        }
        return soundKeys.firstOrNull { it == "shoot" }
            ?: soundKeys.firstOrNull { it == "draw" }
            ?: soundKeys.firstOrNull { it == "reload_tactical" }
            ?: soundKeys.firstOrNull()
    }

    private fun createSyntheticClientMessageContext(): MessageContext? = runCatching {
        val constructor = MessageContext::class.java.getDeclaredConstructor(INetHandler::class.java, Side::class.java)
        constructor.isAccessible = true
        constructor.newInstance(null, Side.CLIENT)
    }.getOrNull()

    private fun reflectStateNames(stateMachine: AnimationStateMachine<*>?): String {
        if (stateMachine == null) {
            return "none"
        }
        return runCatching {
            val field = AnimationStateMachine::class.java.getDeclaredField("currentStates")
            field.isAccessible = true
            val states = field.get(stateMachine) as? Iterable<*>
            (states?.toList() ?: emptyList())
                .mapNotNull { state -> state?.javaClass?.simpleName?.takeIf(String::isNotBlank) ?: state?.javaClass?.name }
                .joinToString("|")
                .ifBlank { "empty" }
        }.getOrElse { error -> "error:${error.javaClass.simpleName}" }
    }

    private fun reflectRunnerSummary(controller: AnimationController?): String {
        if (controller == null) {
            return "none"
        }
        return runCatching {
            val field = AnimationController::class.java.getDeclaredField("currentRunners")
            field.isAccessible = true
            val runners = field.get(controller) as? List<*>
            (runners ?: emptyList<Any?>()).mapIndexedNotNull { index, value ->
                val runner = value as? ObjectAnimationRunner ?: return@mapIndexedNotNull null
                val status = when {
                    runner.isTransitioning -> "transition"
                    runner.isRunning -> "running"
                    runner.isHolding -> "holding"
                    runner.isStopped -> "stopped"
                    runner.isPausing -> "paused"
                    else -> "idle"
                }
                "#$index:${runner.animation.name}:$status"
            }.joinToString("|").ifBlank { "empty" }
        }.getOrElse { error -> "error:${error.javaClass.simpleName}" }
    }

    private fun collectAttachmentSummary(iGun: IGun, stack: net.minecraft.item.ItemStack): String {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val installed = AttachmentType.values()
            .asSequence()
            .filter { it != AttachmentType.NONE }
            .flatMap { type ->
                sequence {
                    val attachmentId = iGun.getAttachmentId(stack, type)
                    if (attachmentId != DefaultAssets.EMPTY_ATTACHMENT_ID) {
                        yield(formatAttachmentEntry(snapshot, type.toString(), attachmentId))
                    }
                    val builtIn = iGun.getBuiltInAttachmentId(stack, type)
                    if (builtIn != DefaultAssets.EMPTY_ATTACHMENT_ID) {
                        yield(formatAttachmentEntry(snapshot, "builtin-$type", builtIn))
                    }
                }
            }
            .toList()
        return installed.joinToString(",").ifBlank { "none" }
    }

    private fun formatAttachmentEntry(
        snapshot: com.tacz.legacy.common.resource.TACZRuntimeSnapshot,
        label: String,
        attachmentId: net.minecraft.util.ResourceLocation,
    ): String {
        val displayId = TACZGunPackPresentation.resolveAttachmentDisplayId(snapshot, attachmentId)
        val index = TACZClientAssetManager.getAttachmentIndex(attachmentId)
        val adapter = index?.adapterNodeName ?: "none"
        val showMuzzle = index?.isShowMuzzle ?: false
        val views = index?.views?.joinToString("|") ?: "none"
        return "$label=$attachmentId(display=${displayId ?: "none"};adapter=$adapter;showMuzzle=$showMuzzle;scope=${index?.isScope ?: false};sight=${index?.isSight ?: false};views=$views)"
    }

    private fun collectAimingPathSummary(model: Any?, stack: net.minecraft.item.ItemStack): String {
        val gunModel = model as? BedrockGunModel ?: return "none"
        return gunModel.resolveAimingViewPath(stack)
            ?.joinToString(">") { part -> part.name ?: "<anon>" }
            ?.ifBlank { "empty" }
            ?: "none"
    }
}

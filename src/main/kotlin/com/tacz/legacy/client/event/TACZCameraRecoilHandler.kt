package com.tacz.legacy.client.event

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZGunPropertyResolver
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction

@SideOnly(Side.CLIENT)
internal object TACZCameraRecoilHandler {
    private var pitchSplineFunction: PolynomialSplineFunction? = null
    private var yawSplineFunction: PolynomialSplineFunction? = null
    private var shootTimestamp: Long = -1L
    private var lastLoggedShootTimestamp: Long = -1L
    private var previousPitchOffset: Double = 0.0
    private var previousYawOffset: Double = 0.0

    @JvmStatic
    internal fun onLocalGunFire(player: EntityPlayerSP, stack: ItemStack) {
        val iGun = stack.item as? IGun ?: return
        val gunData = GunDataAccessor.getGunData(iGun.getGunId(stack)) ?: return
        val operator = IGunOperator.fromLivingEntity(player)
        val recoil = TACZGunPropertyResolver.resolveCameraRecoil(
            stack = stack,
            iGun = iGun,
            gunData = gunData,
            aimingProgress = operator.getSynAimingProgress(),
            isCrawling = operator.getDataHolder().isCrawling,
        ) ?: return
        pitchSplineFunction = recoil.pitch
        yawSplineFunction = recoil.yaw
        shootTimestamp = System.currentTimeMillis()
        previousPitchOffset = 0.0
        previousYawOffset = 0.0
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    @JvmStatic
    internal fun onCameraSetup(event: EntityViewRenderEvent.CameraSetup) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        if (event.entity != player) {
            return
        }
        val elapsed = System.currentTimeMillis() - shootTimestamp
        val elapsedMs = elapsed.toDouble()

        var pitch = event.pitch
        var appliedPitchDelta = 0.0
        pitchSplineFunction?.takeIf { it.isValidPoint(elapsedMs) }?.let { spline ->
            val value = spline.value(elapsedMs)
            appliedPitchDelta = value - previousPitchOffset
            pitch -= appliedPitchDelta.toFloat()
            previousPitchOffset = value
        }

        var yaw = event.yaw
        var appliedYawDelta = 0.0
        yawSplineFunction?.takeIf { it.isValidPoint(elapsedMs) }?.let { spline ->
            val value = spline.value(elapsedMs)
            appliedYawDelta = value - previousYawOffset
            yaw -= appliedYawDelta.toFloat()
            previousYawOffset = value
        }

        if (System.getProperty("tacz.focusedSmoke", "false").toBoolean()
            && shootTimestamp > 0L
            && lastLoggedShootTimestamp != shootTimestamp
            && (kotlin.math.abs(appliedPitchDelta) > 1.0E-4 || kotlin.math.abs(appliedYawDelta) > 1.0E-4)
        ) {
            lastLoggedShootTimestamp = shootTimestamp
            com.tacz.legacy.TACZLegacy.logger.info(
                "[FocusedSmoke] CAMERA_RECOIL_APPLIED shootTimestamp={} pitchDelta={} yawDelta={}",
                shootTimestamp,
                "%.3f".format(appliedPitchDelta),
                "%.3f".format(appliedYawDelta),
            )
        }

        event.pitch = pitch
        event.yaw = yaw
    }
}
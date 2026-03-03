package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class LegacyGunItemStackRendererTest {

    @Test
    public fun `resolve render texture should prefer model then lod then slot then hud texture`() {
        val display = sampleDisplay(
            slotTexturePath = "assets/tacz/textures/gun/slot/ak47.png",
            hudTexturePath = "assets/tacz/textures/gun/hud/ak47.png",
            modelTexturePath = "assets/tacz/textures/gun/uv/ak47.png",
            lodTexturePath = "assets/tacz/textures/gun/lod/ak47.png"
        )

        val resolved = LegacyGunItemStackRenderer.resolveRenderTexturePath(display)

        assertEquals("assets/tacz/textures/gun/uv/ak47.png", resolved)
    }

    @Test
    public fun `resolve render texture should fallback to lod texture when model missing`() {
        val display = sampleDisplay(
            slotTexturePath = "assets/tacz/textures/gun/slot/ak47.png",
            hudTexturePath = "assets/tacz/textures/gun/hud/ak47.png",
            modelTexturePath = null,
            lodTexturePath = "assets/tacz/textures/gun/lod/ak47.png"
        )

        val resolved = LegacyGunItemStackRenderer.resolveRenderTexturePath(display)

        assertEquals("assets/tacz/textures/gun/lod/ak47.png", resolved)
    }

    @Test
    public fun `resolve render texture should fallback to slot then hud texture`() {
        val display = sampleDisplay(
            slotTexturePath = "assets/tacz/textures/gun/slot/ak47.png",
            hudTexturePath = "assets/tacz/textures/gun/hud/ak47.png",
            modelTexturePath = null,
            lodTexturePath = null
        )

        val resolved = LegacyGunItemStackRenderer.resolveRenderTexturePath(display)

        assertEquals("assets/tacz/textures/gun/slot/ak47.png", resolved)
    }

    @Test
    public fun `resolve render texture should return null when all candidates missing`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null
        )

        val resolved = LegacyGunItemStackRenderer.resolveRenderTexturePath(display)

        assertNull(resolved)
    }

    @Test
    public fun `resolve render model should prefer primary model path`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            modelPath = "assets/tacz/geo_models/gun/ak47_geo.json",
            lodModelPath = "assets/tacz/geo_models/gun/lod/ak47.json"
        )

        val resolved = LegacyGunItemStackRenderer.resolveRenderModelPath(display)

        assertEquals("assets/tacz/geo_models/gun/ak47_geo.json", resolved)
    }

    @Test
    public fun `resolve render model should fallback to lod model path`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            modelPath = null,
            lodModelPath = "assets/tacz/geo_models/gun/lod/ak47.json"
        )

        val resolved = LegacyGunItemStackRenderer.resolveRenderModelPath(display)

        assertEquals("assets/tacz/geo_models/gun/lod/ak47.json", resolved)
    }

    @Test
    public fun `resolve animation clip should use explicit display mapping`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            animationClipNames = listOf("animations.ak47.reload"),
            animationReloadClipName = "animations.ak47.reload_tactical"
        )

        val resolved = LegacyGunItemStackRenderer.resolveAnimationClipName(
            display,
            WeaponAnimationClipType.RELOAD
        )

        assertEquals("animations.ak47.reload_tactical", resolved)
    }

    @Test
    public fun `resolve animation clip should fallback by keywords when explicit mapping missing`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            animationClipNames = listOf(
                "animations.ak47.idle",
                "animations.ak47.reload_tactical",
                "animations.ak47.shoot"
            ),
            animationReloadClipName = null
        )

        val resolved = LegacyGunItemStackRenderer.resolveAnimationClipName(
            display,
            WeaponAnimationClipType.RELOAD
        )

        assertEquals("animations.ak47.reload_tactical", resolved)
    }

    @Test
    public fun `resolve fire animation clip should fallback to shot keywords`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            animationClipNames = listOf(
                "animations.ak47.idle",
                "animations.ak47.weapon_recoil"
            ),
            animationFireClipName = null
        )

        val resolved = LegacyGunItemStackRenderer.resolveAnimationClipName(
            display,
            WeaponAnimationClipType.FIRE
        )

        assertEquals("animations.ak47.weapon_recoil", resolved)
    }

    @Test
    public fun `resolve extended animation clips should map draw run and aim keywords`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            animationClipNames = listOf(
                "animations.ak47.draw",
                "animations.ak47.sprint",
                "animations.ak47.ads"
            )
        )

        assertEquals(
            "animations.ak47.draw",
            LegacyGunItemStackRenderer.resolveAnimationClipName(display, WeaponAnimationClipType.DRAW)
        )
        assertEquals(
            "animations.ak47.sprint",
            LegacyGunItemStackRenderer.resolveAnimationClipName(display, WeaponAnimationClipType.RUN)
        )
        assertEquals(
            "animations.ak47.ads",
            LegacyGunItemStackRenderer.resolveAnimationClipName(display, WeaponAnimationClipType.AIM)
        )
    }

    @Test
    public fun `resolve aim animation should avoid fire or reload variants`() {
        val display = sampleDisplay(
            slotTexturePath = null,
            hudTexturePath = null,
            modelTexturePath = null,
            animationClipNames = listOf(
                "animations.ak47.aim_fire",
                "animations.ak47.reload_aim",
                "animations.ak47.aim_idle"
            )
        )

        val resolved = LegacyGunItemStackRenderer.resolveAnimationClipName(
            display,
            WeaponAnimationClipType.AIM
        )

        assertEquals("animations.ak47.aim_idle", resolved)
    }

    @Test
    public fun `additional magazine should be visible only in reload middle window`() {
        assertTrue(
            LegacyGunItemStackRenderer.resolveAdditionalMagazineVisibility(
                clipType = WeaponAnimationClipType.RELOAD,
                progress = 0.5f
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.resolveAdditionalMagazineVisibility(
                clipType = WeaponAnimationClipType.RELOAD,
                progress = 0.03f
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.resolveAdditionalMagazineVisibility(
                clipType = WeaponAnimationClipType.RELOAD,
                progress = 0.97f
            )
        )
    }

    @Test
    public fun `additional magazine should remain hidden outside reload`() {
        assertFalse(
            LegacyGunItemStackRenderer.resolveAdditionalMagazineVisibility(
                clipType = WeaponAnimationClipType.FIRE,
                progress = 0.5f
            )
        )
    }

    @Test
    public fun `default attachment bones should hide when corresponding attachment is installed`() {
        assertFalse(
            LegacyGunItemStackRenderer.resolveDefaultAttachmentBoneVisibility(
                boneName = "scope_default",
                hasScope = true,
                hasMuzzle = false,
                hasStock = false,
                hasGrip = false,
                hasLaser = false,
                hasExtendedMag = false
            )!!
        )

        assertFalse(
            LegacyGunItemStackRenderer.resolveDefaultAttachmentBoneVisibility(
                boneName = "extended_mag_default",
                hasScope = false,
                hasMuzzle = false,
                hasStock = false,
                hasGrip = false,
                hasLaser = false,
                hasExtendedMag = true
            )!!
        )
    }

    @Test
    public fun `default attachment bones should stay visible when attachment missing`() {
        assertTrue(
            LegacyGunItemStackRenderer.resolveDefaultAttachmentBoneVisibility(
                boneName = "muzzle_default",
                hasScope = false,
                hasMuzzle = false,
                hasStock = false,
                hasGrip = false,
                hasLaser = false,
                hasExtendedMag = false
            )!!
        )

        assertTrue(
            LegacyGunItemStackRenderer.resolveDefaultAttachmentBoneVisibility(
                boneName = "stock_default_variant_a",
                hasScope = false,
                hasMuzzle = false,
                hasStock = false,
                hasGrip = false,
                hasLaser = false,
                hasExtendedMag = false
            )!!
        )
    }

    @Test
    public fun `non default bones should not be handled by default attachment helper`() {
        assertNull(
            LegacyGunItemStackRenderer.resolveDefaultAttachmentBoneVisibility(
                boneName = "mount",
                hasScope = true,
                hasMuzzle = true,
                hasStock = true,
                hasGrip = true,
                hasLaser = true,
                hasExtendedMag = true
            )
        )
    }

    @Test
    public fun `camera animation delta should be identity on zero rotation`() {
        val delta = LegacyGunItemStackRenderer.resolveCameraAnimationDeltaFromBoneRotation(
            cameraBoneRotationXDegrees = 0f,
            cameraBoneRotationYDegrees = 0f,
            cameraBoneRotationZDegrees = 0f,
            multiplier = 1f
        )

        assertEquals(0f, delta.pitchDegrees, 1e-5f)
        assertEquals(0f, delta.yawDegrees, 1e-5f)
        assertEquals(0f, delta.rollDegrees, 1e-5f)
        assertEquals(0f, delta.axisAngleDegrees, 1e-5f)
    }

    @Test
    public fun `camera animation delta should invert z roll and support multiplier`() {
        val full = LegacyGunItemStackRenderer.resolveCameraAnimationDeltaFromBoneRotation(
            cameraBoneRotationXDegrees = 0f,
            cameraBoneRotationYDegrees = 0f,
            cameraBoneRotationZDegrees = 20f,
            multiplier = 1f
        )
        val half = LegacyGunItemStackRenderer.resolveCameraAnimationDeltaFromBoneRotation(
            cameraBoneRotationXDegrees = 0f,
            cameraBoneRotationYDegrees = 0f,
            cameraBoneRotationZDegrees = 20f,
            multiplier = 0.5f
        )

        // 对齐 TACZ CameraRotateListener：camera z 旋转写入时取反。
        assertEquals(-20f, full.rollDegrees, 1e-3f)
        // 角轴倍率缩放应近似线性。
        assertEquals(full.rollDegrees * 0.5f, half.rollDegrees, 1e-3f)
        assertTrue(full.axisAngleDegrees > 0f)
        assertTrue(half.axisAngleDegrees in 0f..full.axisAngleDegrees)
    }

    @Test
    public fun `camera animation multiplier should follow TACZ aiming zoom formula`() {
        val hip = LegacyGunItemStackRenderer.resolveCameraAnimationMultiplierFromAiming(
            aimingProgress = 0f,
            aimingZoom = 8f
        )
        val ads = LegacyGunItemStackRenderer.resolveCameraAnimationMultiplierFromAiming(
            aimingProgress = 1f,
            aimingZoom = 8f
        )
        val blend = LegacyGunItemStackRenderer.resolveCameraAnimationMultiplierFromAiming(
            aimingProgress = 0.5f,
            aimingZoom = 8f
        )

        assertEquals(1f, hip, 1e-5f)
        assertEquals((1f / kotlin.math.sqrt(8f)), ads, 1e-5f)
        assertEquals((hip + ads) * 0.5f, blend, 1e-5f)
    }

    @Test
    public fun `aiming progress step should follow aim time and clamp bounds`() {
        val half = LegacyGunItemStackRenderer.resolveAimingProgressStep(
            currentProgress = 0f,
            isAiming = true,
            deltaMillis = 100,
            aimTimeSeconds = 0.2f
        )
        val full = LegacyGunItemStackRenderer.resolveAimingProgressStep(
            currentProgress = half,
            isAiming = true,
            deltaMillis = 300,
            aimTimeSeconds = 0.2f
        )
        val released = LegacyGunItemStackRenderer.resolveAimingProgressStep(
            currentProgress = full,
            isAiming = false,
            deltaMillis = 50,
            aimTimeSeconds = 0.2f
        )

        assertEquals(0.5f, half, 1e-5f)
        assertEquals(1f, full, 1e-5f)
        assertEquals(0.75f, released, 1e-5f)
    }

    @Test
    public fun `aiming progress step should settle instantly when aim time is non positive`() {
        val on = LegacyGunItemStackRenderer.resolveAimingProgressStep(
            currentProgress = 0.3f,
            isAiming = true,
            deltaMillis = 16,
            aimTimeSeconds = 0f
        )
        val off = LegacyGunItemStackRenderer.resolveAimingProgressStep(
            currentProgress = 0.7f,
            isAiming = false,
            deltaMillis = 16,
            aimTimeSeconds = -1f
        )

        assertEquals(1f, on, 1e-5f)
        assertEquals(0f, off, 1e-5f)
    }

    @Test
    public fun `aim loop clip selection should follow aiming progress threshold`() {
        assertFalse(LegacyGunItemStackRenderer.shouldUseAimLoopClip(0f))
        assertFalse(LegacyGunItemStackRenderer.shouldUseAimLoopClip(0.01f))
        assertTrue(LegacyGunItemStackRenderer.shouldUseAimLoopClip(0.02f))
        assertTrue(LegacyGunItemStackRenderer.shouldUseAimLoopClip(0.5f))
    }

    @Test
    public fun `aim loop hysteresis should keep aim active until release threshold`() {
        assertTrue(
            LegacyGunItemStackRenderer.shouldUseAimLoopClipWithHysteresis(
                aimingProgress = 0.015f,
                wasUsingAimLoopClip = true
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.shouldUseAimLoopClipWithHysteresis(
                aimingProgress = 0.009f,
                wasUsingAimLoopClip = true
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.shouldUseAimLoopClipWithHysteresis(
                aimingProgress = 0.015f,
                wasUsingAimLoopClip = false
            )
        )
    }

    @Test
    public fun `runtime playback priority should reserve contextual fallback for low priority loops`() {
        assertTrue(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(WeaponAnimationClipType.DRAW))
        assertTrue(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(WeaponAnimationClipType.FIRE))
        assertFalse(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(WeaponAnimationClipType.IDLE))
        assertFalse(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(WeaponAnimationClipType.WALK))
        assertFalse(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(WeaponAnimationClipType.RUN))
        assertFalse(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(WeaponAnimationClipType.AIM))
        assertFalse(LegacyGunItemStackRenderer.shouldPreferRuntimePlaybackClip(null))
    }

    @Test
    public fun `aiming intent should prefer bridged state and fallback to inputs`() {
        assertTrue(
            LegacyGunItemStackRenderer.resolveAimingIntent(
                bridgedAiming = true,
                useDown = false,
                isSwinging = true
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.resolveAimingIntent(
                bridgedAiming = false,
                useDown = true,
                isSwinging = false
            )
        )

        assertTrue(
            LegacyGunItemStackRenderer.resolveAimingIntent(
                bridgedAiming = null,
                useDown = true,
                isSwinging = false
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.resolveAimingIntent(
                bridgedAiming = null,
                useDown = true,
                isSwinging = true
            )
        )
    }

    @Test
    public fun `first person positioning targets should prioritize iron view when aiming`() {
        val aimingTargets = LegacyGunItemStackRenderer.selectFirstPersonPositioningTargets(preferIronView = true)
        val hipTargets = LegacyGunItemStackRenderer.selectFirstPersonPositioningTargets(preferIronView = false)

        assertEquals(listOf("iron_view", "idle_view", "camera"), aimingTargets)
        assertEquals(listOf("idle_view", "iron_view", "camera"), hipTargets)
    }

    @Test
    public fun `first person positioning blend weight should be continuous and clamped`() {
        assertEquals(0f, LegacyGunItemStackRenderer.resolveFirstPersonPositioningBlendWeight(-1f), 1e-5f)
        assertEquals(0.42f, LegacyGunItemStackRenderer.resolveFirstPersonPositioningBlendWeight(0.42f), 1e-5f)
        assertEquals(1f, LegacyGunItemStackRenderer.resolveFirstPersonPositioningBlendWeight(2f), 1e-5f)
    }

    @Test
    public fun `holding sway transform should be neutral when view and camera offsets match`() {
        val sway = LegacyGunItemStackRenderer.resolveFirstPersonHoldingSwayTransform(
            viewPitchDeltaDegrees = 0f,
            viewYawDeltaDegrees = 0f
        )

        assertEquals(0f, sway.preRotatePitchDegrees, 1e-5f)
        assertEquals(0f, sway.preRotateYawDegrees, 1e-5f)
        assertEquals(0f, sway.offsetX, 1e-5f)
        assertEquals(0f, sway.offsetY, 1e-5f)
        assertEquals(0f, sway.postRotatePitchDegrees, 1e-5f)
        assertEquals(0f, sway.postRotateYawDegrees, 1e-5f)
    }

    @Test
    public fun `holding sway transform should follow TACZ style coefficients`() {
        val sway = LegacyGunItemStackRenderer.resolveFirstPersonHoldingSwayTransform(
            viewPitchDeltaDegrees = 15f,
            viewYawDeltaDegrees = 20f
        )

        assertEquals(-1.5f, sway.preRotatePitchDegrees, 1e-5f)
        assertEquals(2f, sway.preRotateYawDegrees, 1e-5f)
        assertTrue(sway.offsetX > 0f)
        assertTrue(sway.offsetY < 0f)
        assertTrue(sway.postRotatePitchDegrees > 0f)
        assertTrue(sway.postRotateYawDegrees > 0f)
    }

    @Test
    public fun `sway delta angle should use shortest path across plus minus 180 boundary`() {
        val delta = LegacyGunItemStackRenderer.resolveFirstPersonSwayDeltaAngle(
            viewDegrees = -179f,
            armDegrees = 179f
        )

        assertEquals(2f, delta, 1e-5f)
    }

    @Test
    public fun `jump sway progress should rise on jump start and decay in air`() {
        val started = LegacyGunItemStackRenderer.resolveFirstPersonJumpSwayTargetProgress(
            currentProgress = 0f,
            wasOnGround = true,
            isOnGround = false,
            verticalVelocity = 0.42f,
            previousVerticalVelocity = 0f,
            deltaMillis = 50
        )
        val decayed = LegacyGunItemStackRenderer.resolveFirstPersonJumpSwayTargetProgress(
            currentProgress = started,
            wasOnGround = false,
            isOnGround = false,
            verticalVelocity = 0.1f,
            previousVerticalVelocity = 0.42f,
            deltaMillis = 150
        )

        assertEquals(1f, started, 1e-5f)
        assertTrue(decayed < started)
        assertTrue(decayed >= 0f)
    }

    @Test
    public fun `jump sway progress should respond to landing velocity`() {
        val landed = LegacyGunItemStackRenderer.resolveFirstPersonJumpSwayTargetProgress(
            currentProgress = 0.2f,
            wasOnGround = false,
            isOnGround = true,
            verticalVelocity = 0f,
            previousVerticalVelocity = -0.1f,
            deltaMillis = 50
        )

        assertEquals(1f, landed, 1e-5f)
    }

    @Test
    public fun `jump sway offset should map progress into positive y translation`() {
        assertEquals(0f, LegacyGunItemStackRenderer.resolveFirstPersonJumpSwayOffsetY(0f), 1e-5f)
        assertEquals(0.125f, LegacyGunItemStackRenderer.resolveFirstPersonJumpSwayOffsetY(1f), 1e-5f)
    }

    @Test
    public fun `smooth step should approach target and clamp invalid timing`() {
        val half = LegacyGunItemStackRenderer.resolveSmoothedValueStep(
            current = 0f,
            target = 1f,
            deltaMillis = 40,
            smoothTimeSeconds = 0.08f
        )
        val direct = LegacyGunItemStackRenderer.resolveSmoothedValueStep(
            current = 0.3f,
            target = 0.9f,
            deltaMillis = 16,
            smoothTimeSeconds = 0f
        )

        assertEquals(0.5f, half, 1e-5f)
        assertEquals(0.9f, direct, 1e-5f)
    }

    @Test
    public fun `fire sway should decay with fire progress and keep yaw drift under ADS`() {
        val hipStart = LegacyGunItemStackRenderer.resolveFirstPersonShootSwayTransform(
            fireProgress = 0f,
            aimingProgress = 0f,
            elapsedMillis = 0,
            sessionSeed = 0
        )
        val hipEnd = LegacyGunItemStackRenderer.resolveFirstPersonShootSwayTransform(
            fireProgress = 1f,
            aimingProgress = 0f,
            elapsedMillis = 0,
            sessionSeed = 0
        )
        val ads = LegacyGunItemStackRenderer.resolveFirstPersonShootSwayTransform(
            fireProgress = 0f,
            aimingProgress = 1f,
            elapsedMillis = 0,
            sessionSeed = 0
        )

        assertTrue(hipStart.offsetY > 0f)
        assertEquals(0f, hipEnd.offsetY, 1e-5f)
        assertEquals(0f, ads.offsetX, 1e-5f)
        assertEquals(0f, ads.offsetY, 1e-5f)
        assertTrue(kotlin.math.abs(ads.yawDegrees) > 1e-5f)
    }

    @Test
    public fun `fire sway yaw noise should be deterministic for same seed and elapsed`() {
        val a = LegacyGunItemStackRenderer.resolveFirstPersonShootSwayTransform(
            fireProgress = 0.25f,
            aimingProgress = 0.4f,
            elapsedMillis = 87,
            sessionSeed = 42
        )
        val b = LegacyGunItemStackRenderer.resolveFirstPersonShootSwayTransform(
            fireProgress = 0.25f,
            aimingProgress = 0.4f,
            elapsedMillis = 87,
            sessionSeed = 42
        )
        val c = LegacyGunItemStackRenderer.resolveFirstPersonShootSwayTransform(
            fireProgress = 0.25f,
            aimingProgress = 0.4f,
            elapsedMillis = 187,
            sessionSeed = 42
        )

        assertEquals(a.offsetX, b.offsetX, 1e-6f)
        assertEquals(a.offsetY, b.offsetY, 1e-6f)
        assertEquals(a.yawDegrees, b.yawDegrees, 1e-6f)
        assertTrue(kotlin.math.abs(a.yawDegrees - c.yawDegrees) > 1e-4f)
    }

    @Test
    public fun `ease out cubic should be bounded and monotonic`() {
        val zero = LegacyGunItemStackRenderer.easeOutCubic01(0f)
        val mid = LegacyGunItemStackRenderer.easeOutCubic01(0.5f)
        val one = LegacyGunItemStackRenderer.easeOutCubic01(1f)

        assertEquals(0f, zero, 1e-5f)
        assertTrue(mid in 0f..1f)
        assertEquals(1f, one, 1e-5f)
        assertTrue(mid > zero)
        assertTrue(one > mid)
    }

    @Test
    public fun `animation sound trigger should fire on first sample and sequential progress`() {
        assertTrue(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = null,
                currentSampleSeconds = 0.1f,
                soundTimeSeconds = 0.05f,
                wrapped = false
            )
        )

        assertTrue(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = 0.1f,
                currentSampleSeconds = 0.2f,
                soundTimeSeconds = 0.15f,
                wrapped = false
            )
        )

        assertFalse(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = 0.1f,
                currentSampleSeconds = 0.2f,
                soundTimeSeconds = 0.25f,
                wrapped = false
            )
        )
    }

    @Test
    public fun `animation sound trigger should handle loop wrap around`() {
        assertTrue(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = 0.9f,
                currentSampleSeconds = 0.1f,
                soundTimeSeconds = 0.95f,
                wrapped = true
            )
        )

        assertTrue(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = 0.9f,
                currentSampleSeconds = 0.1f,
                soundTimeSeconds = 0.05f,
                wrapped = true
            )
        )
    }

    @Test
    public fun `animation sound trigger should include start boundary when sample advances`() {
        assertTrue(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = 0.1f,
                currentSampleSeconds = 0.2f,
                soundTimeSeconds = 0.1f,
                wrapped = false
            )
        )
    }

    @Test
    public fun `animation sound trigger should not replay when sample does not advance`() {
        assertFalse(
            LegacyGunItemStackRenderer.shouldTriggerAnimationSound(
                previousSampleSeconds = 0.2f,
                currentSampleSeconds = 0.2f,
                soundTimeSeconds = 0.2f,
                wrapped = false
            )
        )
    }

    @Test
    public fun `animation sound candidates should keep explicit namespace`() {
        val candidates = LegacyGunItemStackRenderer.resolveAnimationSoundResourceCandidates(
            soundId = "tacz:p90/p90_reload_maggrab",
            displayResource = "other_pack:p90_display",
            animationPath = "assets/other_pack/animations/p90.animation.json"
        )

        assertEquals(listOf("tacz:p90/p90_reload_maggrab"), candidates)
    }

    @Test
    public fun `animation sound candidates should fallback to display namespace then defaults`() {
        val candidates = LegacyGunItemStackRenderer.resolveAnimationSoundResourceCandidates(
            soundId = "m4a1_raise_v2",
            displayResource = "custompack:m4a1_display",
            animationPath = "assets/tacz/animations/m16a1.animation.json"
        )

        assertEquals(
            listOf(
                "custompack:m4a1_raise_v2",
                "tacz:m4a1_raise_v2",
                "minecraft:m4a1_raise_v2"
            ),
            candidates
        )
    }

    @Test
    public fun `animation sound candidates should fallback to animation namespace when display namespace missing`() {
        val candidates = LegacyGunItemStackRenderer.resolveAnimationSoundResourceCandidates(
            soundId = "ump45_raise_quick",
            displayResource = "invalid-display-resource",
            animationPath = "assets/legacypack/animations/ump45.animation.json"
        )

        assertEquals(
            listOf(
                "legacypack:ump45_raise_quick",
                "tacz:ump45_raise_quick",
                "minecraft:ump45_raise_quick"
            ),
            candidates
        )
    }

    @Test
    public fun `animation sound candidates should de duplicate overlapping namespaces`() {
        val candidates = LegacyGunItemStackRenderer.resolveAnimationSoundResourceCandidates(
            soundId = "ak47/inspect_1",
            displayResource = "tacz:ak47_display",
            animationPath = "assets/tacz/animations/ak47.animation.json"
        )

        assertEquals(
            listOf(
                "tacz:ak47/inspect_1",
                "minecraft:ak47/inspect_1"
            ),
            candidates
        )
    }

    @Test
    public fun `animation sound replay guard should suppress same id within interval`() {
        assertTrue(
            LegacyGunItemStackRenderer.shouldSuppressAnimationSoundReplay(
                previousSoundId = "tacz:m4a1_raise_v2",
                previousPlayedAtMillis = 1_000L,
                previousClipName = "animations.m16a1.draw",
                currentSoundId = "tacz:m4a1_raise_v2",
                currentClipName = "animations.m16a1.draw",
                nowMillis = 1_010L,
                minIntervalMillis = 20L
            )
        )
    }

    @Test
    public fun `animation sound replay guard should allow same id after interval`() {
        assertFalse(
            LegacyGunItemStackRenderer.shouldSuppressAnimationSoundReplay(
                previousSoundId = "tacz:m4a1_raise_v2",
                previousPlayedAtMillis = 1_000L,
                previousClipName = "animations.m16a1.draw",
                currentSoundId = "tacz:m4a1_raise_v2",
                currentClipName = "animations.m16a1.draw",
                nowMillis = 1_030L,
                minIntervalMillis = 20L
            )
        )
    }

    @Test
    public fun `animation sound replay guard should not suppress different ids`() {
        assertFalse(
            LegacyGunItemStackRenderer.shouldSuppressAnimationSoundReplay(
                previousSoundId = "tacz:m4a1_raise_v2",
                previousPlayedAtMillis = 1_000L,
                previousClipName = "animations.m16a1.draw",
                currentSoundId = "tacz:p90/p90_reload_maggrab",
                currentClipName = "animations.m16a1.draw",
                nowMillis = 1_005L,
                minIntervalMillis = 20L
            )
        )
    }

    @Test
    public fun `animation sound replay guard should allow same id across different clips`() {
        assertFalse(
            LegacyGunItemStackRenderer.shouldSuppressAnimationSoundReplay(
                previousSoundId = "tacz:m4a1_raise_v2",
                previousPlayedAtMillis = 1_000L,
                previousClipName = "animations.m16a1.draw",
                currentSoundId = "tacz:m4a1_raise_v2",
                currentClipName = "animations.m16a1.put_away",
                nowMillis = 1_005L,
                minIntervalMillis = 20L
            )
        )
    }

    @Test
    public fun `animation sound replay key should differentiate keyframe timestamps`() {
        val keyA = LegacyGunItemStackRenderer.buildAnimationSoundReplayKey(
            soundId = "tacz:m4a1_raise_v2",
            clipName = "animations.m16a1.reload",
            keyframeTimeSeconds = 0.125f
        )
        val keyB = LegacyGunItemStackRenderer.buildAnimationSoundReplayKey(
            soundId = "tacz:m4a1_raise_v2",
            clipName = "animations.m16a1.reload",
            keyframeTimeSeconds = 0.2917f
        )

        assertTrue(keyA != null)
        assertTrue(keyB != null)
        assertTrue(keyA != keyB)
    }

    @Test
    public fun `animation sound replay key should preserve sub millisecond differences`() {
        val keyA = LegacyGunItemStackRenderer.buildAnimationSoundReplayKey(
            soundId = "tacz:m4a1_raise_v2",
            clipName = "animations.m16a1.reload",
            keyframeTimeSeconds = 0.1231f
        )
        val keyB = LegacyGunItemStackRenderer.buildAnimationSoundReplayKey(
            soundId = "tacz:m4a1_raise_v2",
            clipName = "animations.m16a1.reload",
            keyframeTimeSeconds = 0.1234f
        )

        assertTrue(keyA != null)
        assertTrue(keyB != null)
        assertTrue(keyA != keyB)
    }

    @Test
    public fun `animation sound replay guard by key should allow same id on different keyframes`() {
        val previousKey = LegacyGunItemStackRenderer.buildAnimationSoundReplayKey(
            soundId = "tacz:m4a1_raise_v2",
            clipName = "animations.m16a1.reload",
            keyframeTimeSeconds = 0.125f
        )
        val currentKey = LegacyGunItemStackRenderer.buildAnimationSoundReplayKey(
            soundId = "tacz:m4a1_raise_v2",
            clipName = "animations.m16a1.reload",
            keyframeTimeSeconds = 0.2917f
        )

        assertFalse(
            LegacyGunItemStackRenderer.shouldSuppressAnimationSoundReplayByKey(
                previousReplayKey = previousKey,
                currentReplayKey = currentKey ?: "",
                previousPlayedAtMillis = 1_000L,
                nowMillis = 1_010L,
                minIntervalMillis = 20L
            )
        )
    }

    @Test
    public fun `animation sound replay guard by key should normalize case and whitespace`() {
        assertTrue(
            LegacyGunItemStackRenderer.shouldSuppressAnimationSoundReplayByKey(
                previousReplayKey = "  Animations.M16A1.Reload|TACZ:M4A1_Raise_V2|EVT ",
                currentReplayKey = "animations.m16a1.reload|tacz:m4a1_raise_v2|evt",
                previousPlayedAtMillis = 5_000L,
                nowMillis = 5_010L,
                minIntervalMillis = 20L
            )
        )
    }

    @Test
    public fun `animation sound payload parser should support string shorthand`() {
        val payload = JsonParser().parse("\"tacz:ak47/ak47_raise\"")
        val parsed = LegacyGunItemStackRenderer.parseAnimationSoundEffectPayload(payload)

        assertEquals(1, parsed.size)
        assertEquals("tacz:ak47/ak47_raise", parsed[0].effectId)
        assertEquals(1f, parsed[0].volume, 1e-5f)
        assertEquals(1f, parsed[0].pitch, 1e-5f)
    }

    @Test
    public fun `animation sound payload parser should support mixed array payload`() {
        val payload = JsonParser().parse(
            """
            [
              "tacz:ak47/ak47_raise",
              { "effect": "tacz:ak47/ak47_magin", "volume": 1.2, "pitch": 1.8 },
              { "effects": ["tacz:ak47/ak47_end"] }
            ]
            """.trimIndent()
        )
        val parsed = LegacyGunItemStackRenderer.parseAnimationSoundEffectPayload(payload)

        assertEquals(3, parsed.size)
        assertEquals("tacz:ak47/ak47_raise", parsed[0].effectId)
        assertEquals("tacz:ak47/ak47_magin", parsed[1].effectId)
        assertEquals(1.2f, parsed[1].volume, 1e-5f)
        assertEquals(1.8f, parsed[1].pitch, 1e-5f)
        assertEquals("tacz:ak47/ak47_end", parsed[2].effectId)
    }

    @Test
    public fun `default animation profile should map pistol to pistol default`() {
        assertEquals(
            "assets/tacz/animations/pistol_default.animation.json",
            LegacyGunItemStackRenderer.resolveDefaultAnimationAssetPath("pistol")
        )
    }

    @Test
    public fun `default animation profile should fallback to rifle default for unknown`() {
        assertEquals(
            "assets/tacz/animations/rifle_default.animation.json",
            LegacyGunItemStackRenderer.resolveDefaultAnimationAssetPath("shotgun")
        )
    }

    private fun sampleDisplay(
        slotTexturePath: String?,
        hudTexturePath: String?,
        modelTexturePath: String?,
        lodTexturePath: String? = null,
        modelPath: String? = "assets/tacz/geo_models/gun/ak47_geo.json",
        lodModelPath: String? = null,
        animationClipNames: List<String>? = null,
        animationReloadClipName: String? = null,
        animationFireClipName: String? = null
    ): GunDisplayDefinition =
        GunDisplayDefinition(
            sourceId = "sample_pack/assets/tacz/display/guns/ak47_display.json",
            gunId = "ak47",
            displayResource = "tacz:ak47_display",
            modelPath = modelPath,
            modelTexturePath = modelTexturePath,
            lodModelPath = lodModelPath,
            lodTexturePath = lodTexturePath,
            slotTexturePath = slotTexturePath,
            animationPath = null,
            stateMachinePath = null,
            playerAnimator3rdPath = null,
            thirdPersonAnimation = null,
            modelParseSucceeded = true,
            modelBoneCount = 1,
            modelCubeCount = 2,
            animationParseSucceeded = false,
            animationClipCount = null,
            animationClipNames = animationClipNames,
            animationFireClipName = animationFireClipName,
            animationReloadClipName = animationReloadClipName,
            stateMachineResolved = false,
            playerAnimatorResolved = false,
            hudTexturePath = hudTexturePath,
            hudEmptyTexturePath = null,
            showCrosshair = true,
            modelGeometryCount = 1,
            modelRootBoneCount = 1,
            modelRootBoneNames = listOf("body")
        )

}

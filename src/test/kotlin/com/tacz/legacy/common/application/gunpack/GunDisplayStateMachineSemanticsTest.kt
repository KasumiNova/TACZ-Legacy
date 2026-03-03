package com.tacz.legacy.common.application.gunpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class GunDisplayStateMachineSemanticsTest {

    @Test
    public fun `manual bolt state machine detection should match known script names`() {
        assertTrue(
            GunDisplayStateMachineSemantics.isManualBoltStateMachine(
                "assets/tacz/scripts/manual_action_state_machine.lua"
            )
        )
        assertTrue(
            GunDisplayStateMachineSemantics.isManualBoltStateMachine(
                "assets/tacz/scripts/m870_state_machine.lua"
            )
        )
        assertFalse(
            GunDisplayStateMachineSemantics.isManualBoltStateMachine(
                "assets/tacz/scripts/default_state_machine.lua"
            )
        )
    }

    @Test
    public fun `state machine param millis conversion should clamp negative and reject invalid`() {
        assertEquals(250L, GunDisplayStateMachineSemantics.resolveStateMachineParamMillis(0.25f))
        assertEquals(0L, GunDisplayStateMachineSemantics.resolveStateMachineParamMillis(-0.2f))
        assertEquals(null, GunDisplayStateMachineSemantics.resolveStateMachineParamMillis(Float.NaN))
    }

    @Test
    public fun `bolt and intro shell timing extraction should resolve from params map`() {
        val params = mapOf(
            GunDisplayStateMachineSemantics.BOLT_SHELL_EJECTING_TIME_KEY to 0.17f,
            GunDisplayStateMachineSemantics.INTRO_SHELL_EJECTING_TIME_KEY to 0.4f
        )

        assertTrue(GunDisplayStateMachineSemantics.hasBoltShellEjectingTime(params))
        assertEquals(170L, GunDisplayStateMachineSemantics.resolveBoltShellEjectingTimeMillis(params))
        assertEquals(400L, GunDisplayStateMachineSemantics.resolveIntroShellEjectingTimeMillis(params))
    }

    @Test
    public fun `missing bolt shell timing should report false`() {
        val params = mapOf(
            GunDisplayStateMachineSemantics.INTRO_SHELL_EJECTING_TIME_KEY to 0.4f
        )
        assertFalse(GunDisplayStateMachineSemantics.hasBoltShellEjectingTime(params))
        assertEquals(null, GunDisplayStateMachineSemantics.resolveBoltShellEjectingTimeMillis(params))
    }

    @Test
    public fun `bolt clip detection should support explicit and inferred names`() {
        assertTrue(
            GunDisplayStateMachineSemantics.hasBoltClip(
                explicitBoltClipName = "pull_bolt",
                animationClipNames = listOf("idle", "shoot")
            )
        )

        assertTrue(
            GunDisplayStateMachineSemantics.hasBoltClip(
                explicitBoltClipName = null,
                animationClipNames = listOf("animation.ak47.blot")
            )
        )

        assertFalse(
            GunDisplayStateMachineSemantics.hasBoltClip(
                explicitBoltClipName = null,
                animationClipNames = listOf("animation.ak47.reload_tactical", "animation.ak47.idle")
            )
        )
    }

    @Test
    public fun `prefer bolt cycle after fire should require bolt clip and manual or bolt timing semantics`() {
        val manualWithBolt = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
            animationBoltClipName = "pull_bolt"
        )
        assertTrue(GunDisplayStateMachineSemantics.shouldPreferBoltCycleAfterFire(manualWithBolt))

        val paramWithInferredBolt = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/default_state_machine.lua",
            stateMachineParams = mapOf(GunDisplayStateMachineSemantics.BOLT_SHELL_EJECTING_TIME_KEY to 0.12f),
            animationClipNames = listOf("idle", "animation.ak47.bolt")
        )
        assertTrue(GunDisplayStateMachineSemantics.shouldPreferBoltCycleAfterFire(paramWithInferredBolt))

        val manualWithoutBolt = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
            animationClipNames = listOf("idle", "shoot")
        )
        assertFalse(GunDisplayStateMachineSemantics.shouldPreferBoltCycleAfterFire(manualWithoutBolt))
    }

    @Test
    public fun `shell eject timing profile should map params and bolt fallback consistently`() {
        val manualWithBolt = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
            stateMachineParams = mapOf(
                GunDisplayStateMachineSemantics.BOLT_SHELL_EJECTING_TIME_KEY to 0.13f,
                GunDisplayStateMachineSemantics.INTRO_SHELL_EJECTING_TIME_KEY to 0.25f
            ),
            animationBoltClipName = "pull_bolt"
        )

        val manualProfile = GunDisplayStateMachineSemantics.resolveShellEjectTimingProfile(manualWithBolt)
        assertEquals(null, manualProfile.fireTriggerMillis)
        assertEquals(250L, manualProfile.reloadTriggerMillis)
        assertEquals(130L, manualProfile.boltTriggerMillis)

        val defaultNoBolt = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/default_state_machine.lua",
            animationClipNames = listOf("idle", "shoot")
        )
        val defaultProfile = GunDisplayStateMachineSemantics.resolveShellEjectTimingProfile(defaultNoBolt)
        assertEquals(0L, defaultProfile.fireTriggerMillis)
        assertEquals(null, defaultProfile.reloadTriggerMillis)
        assertEquals(null, defaultProfile.boltTriggerMillis)
    }

    @Test
    public fun `shell eject timing profile should fallback to gun script params when display params are missing`() {
        val displayWithBolt = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/default_state_machine.lua",
            animationBoltClipName = "pull_bolt",
            animationClipNames = listOf("idle", "pull_bolt")
        )

        val profile = GunDisplayStateMachineSemantics.resolveShellEjectTimingProfile(
            displayDefinition = displayWithBolt,
            gunScriptParams = mapOf(
                GunDisplayStateMachineSemantics.SCRIPT_BOLT_FEED_TIME_KEY to 0.11f,
                GunDisplayStateMachineSemantics.SCRIPT_SHOOT_FEED_TIME_KEY to 0.23f
            )
        )

        assertEquals(null, profile.fireTriggerMillis)
        assertEquals(230L, profile.reloadTriggerMillis)
        assertEquals(110L, profile.boltTriggerMillis)
    }

    private fun displayDefinition(
        stateMachinePath: String,
        stateMachineParams: Map<String, Float> = emptyMap(),
        animationBoltClipName: String? = null,
        animationClipNames: List<String>? = null
    ): GunDisplayDefinition = GunDisplayDefinition(
        sourceId = "sample_pack/data/tacz/data/guns/shotgun/m870_display.json",
        gunId = "m870",
        displayResource = "tacz:gun/m870_display",
        modelPath = null,
        modelTexturePath = null,
        lodModelPath = null,
        lodTexturePath = null,
        slotTexturePath = null,
        animationPath = null,
        stateMachinePath = stateMachinePath,
        stateMachineParams = stateMachineParams,
        playerAnimator3rdPath = null,
        thirdPersonAnimation = null,
        modelParseSucceeded = true,
        modelBoneCount = null,
        modelCubeCount = null,
        animationParseSucceeded = true,
        animationClipCount = animationClipNames?.size,
        animationClipNames = animationClipNames,
        animationBoltClipName = animationBoltClipName,
        stateMachineResolved = true,
        playerAnimatorResolved = true,
        hudTexturePath = null,
        hudEmptyTexturePath = null,
        showCrosshair = true
    )

}

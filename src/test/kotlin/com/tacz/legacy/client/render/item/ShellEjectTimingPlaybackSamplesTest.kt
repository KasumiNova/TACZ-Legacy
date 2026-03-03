package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.gunpack.GunDisplayStateMachineSemantics
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class ShellEjectTimingPlaybackSamplesTest {

    @Test
    public fun `playback sample - manual bolt should eject on bolt timing`() {
        val definition = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
            stateMachineParams = mapOf("bolt_shell_ejecting_time" to 0.12f),
            animationBoltClipName = "bolt"
        )

        val timeline = listOf(
            Frame(WeaponAnimationClipType.FIRE, 0L),
            Frame(WeaponAnimationClipType.FIRE, 40L),
            Frame(WeaponAnimationClipType.BOLT, 80L),
            Frame(WeaponAnimationClipType.BOLT, 120L),
            Frame(WeaponAnimationClipType.BOLT, 180L)
        )

        val spawnAt = replayFallbackSpawnFrames(
            displayDefinition = definition,
            timeline = timeline
        )

        assertEquals(listOf(3), spawnAt)
    }

    @Test
    public fun `playback sample - pump reload should eject at intro timing`() {
        val definition = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/shotgun_state_machine.lua",
            stateMachineParams = mapOf("intro_shell_ejecting_time" to 0.18f),
            animationBoltClipName = "bolt"
        )

        val timeline = listOf(
            Frame(WeaponAnimationClipType.RELOAD, 100L),
            Frame(WeaponAnimationClipType.RELOAD, 170L),
            Frame(WeaponAnimationClipType.RELOAD, 181L),
            Frame(WeaponAnimationClipType.RELOAD, 240L)
        )

        val spawnAt = replayFallbackSpawnFrames(
            displayDefinition = definition,
            timeline = timeline
        )

        assertEquals(listOf(2), spawnAt)
    }

    @Test
    public fun `playback sample - semi auto should eject on each fire cycle`() {
        val definition = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/default_state_machine.lua",
            stateMachineParams = emptyMap(),
            animationBoltClipName = null
        )

        val timeline = listOf(
            Frame(WeaponAnimationClipType.FIRE, 0L),
            Frame(WeaponAnimationClipType.FIRE, 16L),
            Frame(WeaponAnimationClipType.FIRE, 70L),
            Frame(WeaponAnimationClipType.IDLE, 0L),
            Frame(WeaponAnimationClipType.FIRE, 8L)
        )

        val spawnAt = replayFallbackSpawnFrames(
            displayDefinition = definition,
            timeline = timeline
        )

        assertEquals(listOf(0, 4), spawnAt)
    }

    @Test
    public fun `playback sample - manual path without bolt clip should not suppress fire eject`() {
        val definition = displayDefinition(
            stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
            stateMachineParams = emptyMap(),
            animationBoltClipName = null,
            animationClipNames = listOf("idle", "shoot")
        )

        assertFalse(FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(definition))
        assertTrue(GunDisplayStateMachineSemantics.shouldPreferBoltCycleAfterFire(definition).not())
    }

    private fun replayFallbackSpawnFrames(
        displayDefinition: GunDisplayDefinition,
        timeline: List<Frame>
    ): List<Int> {
        var lastClip: WeaponAnimationClipType? = null
        var lastElapsed: Long = Long.MAX_VALUE
        var spawnedInCycle = false
        val spawnFrames: MutableList<Int> = mutableListOf()

        timeline.forEachIndexed { index, frame ->
            val restarted = FirstPersonShellEjectRenderer.hasClipElapsedRestarted(
                previousElapsedMillis = lastElapsed,
                currentElapsedMillis = frame.elapsedMillis,
                epsilonMillis = 2L
            )
            if (frame.clip != lastClip || restarted) {
                spawnedInCycle = false
            }

            val directive = resolveDirective(displayDefinition, frame.clip)
            val spawned = directive?.let {
                FirstPersonShellEjectRenderer.shouldSpawnShellAtTriggerInCurrentFrame(
                    previousClipType = lastClip,
                    previousElapsedMillis = lastElapsed,
                    currentClipType = frame.clip,
                    currentElapsedMillis = frame.elapsedMillis,
                    spawnedInCurrentCycle = spawnedInCycle,
                    targetClipType = it.clipType,
                    triggerMillis = it.triggerMillis,
                    triggerWindowMillis = it.triggerWindowMillis
                )
            } ?: false

            if (spawned) {
                spawnFrames += index
                spawnedInCycle = true
            }

            lastClip = frame.clip
            lastElapsed = frame.elapsedMillis
        }

        return spawnFrames
    }

    private fun resolveDirective(
        displayDefinition: GunDisplayDefinition,
        currentClipType: WeaponAnimationClipType
    ): Directive? {
        val params = displayDefinition.stateMachineParams
        return when (currentClipType) {
            WeaponAnimationClipType.FIRE -> {
                if (FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(displayDefinition)) {
                    null
                } else {
                    Directive(
                        clipType = WeaponAnimationClipType.FIRE,
                        triggerMillis = 0L,
                        triggerWindowMillis = 45L
                    )
                }
            }

            WeaponAnimationClipType.RELOAD -> {
                GunDisplayStateMachineSemantics.resolveIntroShellEjectingTimeMillis(params)
                    ?.let { introMillis ->
                        Directive(
                            clipType = WeaponAnimationClipType.RELOAD,
                            triggerMillis = introMillis,
                            triggerWindowMillis = 220L
                        )
                    }
            }

            WeaponAnimationClipType.BOLT -> {
                Directive(
                    clipType = WeaponAnimationClipType.BOLT,
                    triggerMillis = FirstPersonShellEjectRenderer.resolveBoltShellTriggerMillis(params),
                    triggerWindowMillis = 220L
                )
            }

            else -> null
        }
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

    private data class Frame(
        val clip: WeaponAnimationClipType,
        val elapsedMillis: Long
    )

    private data class Directive(
        val clipType: WeaponAnimationClipType,
        val triggerMillis: Long,
        val triggerWindowMillis: Long
    )
}

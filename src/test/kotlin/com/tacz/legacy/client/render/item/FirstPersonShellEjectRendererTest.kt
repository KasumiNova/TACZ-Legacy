package com.tacz.legacy.client.render.item

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationClipType
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEvent
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAnimationRuntimeEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class FirstPersonShellEjectRendererTest {

    @Test
    public fun `spawn decision should trigger at fire start and fire restart`() {
        assertTrue(
            FirstPersonShellEjectRenderer.shouldSpawnShellInCurrentFrame(
                previousClipType = WeaponAnimationClipType.IDLE,
                previousElapsedMillis = 999L,
                currentClipType = WeaponAnimationClipType.FIRE,
                currentElapsedMillis = 10L,
                spawnedInCurrentFire = false
            )
        )

        assertTrue(
            FirstPersonShellEjectRenderer.shouldSpawnShellInCurrentFrame(
                previousClipType = WeaponAnimationClipType.FIRE,
                previousElapsedMillis = 60L,
                currentClipType = WeaponAnimationClipType.FIRE,
                currentElapsedMillis = 5L,
                spawnedInCurrentFire = true
            )
        )
    }

    @Test
    public fun `spawn decision should still trigger when first observed fire frame is beyond spawn window`() {
        assertTrue(
            FirstPersonShellEjectRenderer.shouldSpawnShellInCurrentFrame(
                previousClipType = WeaponAnimationClipType.IDLE,
                previousElapsedMillis = Long.MAX_VALUE,
                currentClipType = WeaponAnimationClipType.FIRE,
                currentElapsedMillis = 80L,
                spawnedInCurrentFire = false
            )
        )
    }

    @Test
    public fun `spawn decision should trigger on fire restart even if restart frame elapsed exceeds window`() {
        assertTrue(
            FirstPersonShellEjectRenderer.shouldSpawnShellInCurrentFrame(
                previousClipType = WeaponAnimationClipType.FIRE,
                previousElapsedMillis = 120L,
                currentClipType = WeaponAnimationClipType.FIRE,
                currentElapsedMillis = 50L,
                spawnedInCurrentFire = true
            )
        )
    }

    @Test
    public fun `spawn decision should suppress duplicate spawn in same fire window`() {
        assertFalse(
            FirstPersonShellEjectRenderer.shouldSpawnShellInCurrentFrame(
                previousClipType = WeaponAnimationClipType.FIRE,
                previousElapsedMillis = 12L,
                currentClipType = WeaponAnimationClipType.FIRE,
                currentElapsedMillis = 15L,
                spawnedInCurrentFire = true
            )
        )

        assertFalse(
            FirstPersonShellEjectRenderer.shouldSpawnShellInCurrentFrame(
                previousClipType = WeaponAnimationClipType.FIRE,
                previousElapsedMillis = 12L,
                currentClipType = WeaponAnimationClipType.FIRE,
                currentElapsedMillis = 70L,
                spawnedInCurrentFire = false
            )
        )
    }

    @Test
    public fun `timed trigger decision should spawn in reload window and suppress duplicates`() {
        assertTrue(
            FirstPersonShellEjectRenderer.shouldSpawnShellAtTriggerInCurrentFrame(
                previousClipType = WeaponAnimationClipType.RELOAD,
                previousElapsedMillis = 360L,
                currentClipType = WeaponAnimationClipType.RELOAD,
                currentElapsedMillis = 420L,
                spawnedInCurrentCycle = false,
                targetClipType = WeaponAnimationClipType.RELOAD,
                triggerMillis = 400L,
                triggerWindowMillis = 220L
            )
        )

        assertFalse(
            FirstPersonShellEjectRenderer.shouldSpawnShellAtTriggerInCurrentFrame(
                previousClipType = WeaponAnimationClipType.RELOAD,
                previousElapsedMillis = 420L,
                currentClipType = WeaponAnimationClipType.RELOAD,
                currentElapsedMillis = 460L,
                spawnedInCurrentCycle = true,
                targetClipType = WeaponAnimationClipType.RELOAD,
                triggerMillis = 400L,
                triggerWindowMillis = 220L
            )
        )
    }

    @Test
    public fun `manual action state machine should suppress immediate fire shell spawn`() {
        assertTrue(
            FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(
                "assets/tacz/scripts/manual_action_state_machine.lua"
            )
        )
        assertFalse(
            FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(
                "assets/tacz/scripts/ak47_state_machine.lua"
            )
        )

        assertTrue(
            FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(
                stateMachinePath = "assets/tacz/scripts/default_state_machine.lua",
                stateMachineParams = mapOf("bolt_shell_ejecting_time" to 0.11f)
            )
        )

        assertFalse(
            FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(
                stateMachinePath = "assets/tacz/scripts/default_state_machine.lua",
                stateMachineParams = mapOf("intro_shell_ejecting_time" to 0.20f)
            )
        )

        assertFalse(
            FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(
                displayDefinition = displayDefinition(
                    stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
                    animationClipNames = listOf("idle", "shoot")
                )
            )
        )

        assertTrue(
            FirstPersonShellEjectRenderer.shouldSuppressFireShellSpawn(
                displayDefinition = displayDefinition(
                    stateMachinePath = "assets/tacz/scripts/manual_action_state_machine.lua",
                    animationBoltClipName = "pull_bolt"
                )
            )
        )
    }

    @Test
    public fun `state machine param seconds should convert to millis`() {
        assertEquals(400L, FirstPersonShellEjectRenderer.resolveStateMachineParamMillis(0.4f))
        assertEquals(0L, FirstPersonShellEjectRenderer.resolveStateMachineParamMillis(-1f))
    }

    @Test
    public fun `bolt shell trigger millis should fallback to zero when param missing`() {
        assertEquals(
            0L,
            FirstPersonShellEjectRenderer.resolveBoltShellTriggerMillis(
                stateMachineParams = emptyMap()
            )
        )
        assertEquals(
            160L,
            FirstPersonShellEjectRenderer.resolveBoltShellTriggerMillis(
                stateMachineParams = mapOf("bolt_shell_ejecting_time" to 0.16f)
            )
        )
    }

    @Test
    public fun `shell scale should shrink in ads`() {
        val hip = FirstPersonShellEjectRenderer.resolveShellScale(0f)
        val ads = FirstPersonShellEjectRenderer.resolveShellScale(1f)

        assertTrue(hip > ads)
        assertTrue(ads > 0f)
    }

    @Test
    public fun `integration step should apply gravity and displacement`() {
        val (nextPos, nextVel) = FirstPersonShellEjectRenderer.integrateShellStep(
            position = FirstPersonShellEjectRenderer.ShellVec3(0f, 0f, 0f),
            velocity = FirstPersonShellEjectRenderer.ShellVec3(1f, 1f, 0f),
            deltaSeconds = 0.1f,
            gravityPerSecond = 2f
        )

        assertEquals(1f, nextVel.x, 1e-6f)
        assertEquals(0.8f, nextVel.y, 1e-6f)
        assertEquals(0.1f, nextPos.x, 1e-6f)
        assertEquals(0.08f, nextPos.y, 1e-6f)
    }

    @Test
    public fun `unconsumed shell eject events should be filtered by sequence and sorted`() {
        val events = listOf(
            WeaponAnimationRuntimeEvent(
                sequence = 4L,
                type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
                clip = WeaponAnimationClipType.BOLT,
                emittedAtMillis = 1_020L
            ),
            WeaponAnimationRuntimeEvent(
                sequence = 2L,
                type = WeaponAnimationRuntimeEventType.SHELL_EJECT,
                clip = WeaponAnimationClipType.FIRE,
                emittedAtMillis = 1_000L
            )
        )

        val unconsumed = FirstPersonShellEjectRenderer.resolveUnconsumedShellEjectEvents(
            transientEvents = events,
            lastConsumedSequence = 2L
        )

        assertEquals(1, unconsumed.size)
        assertEquals(4L, unconsumed.first().sequence)
        assertEquals(WeaponAnimationClipType.BOLT, unconsumed.first().clip)
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

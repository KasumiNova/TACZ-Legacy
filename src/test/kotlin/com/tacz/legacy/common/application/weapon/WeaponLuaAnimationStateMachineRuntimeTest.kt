package com.tacz.legacy.common.application.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.domain.weapon.WeaponStepResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

public class WeaponLuaAnimationStateMachineRuntimeTest {

    @After
    public fun cleanup() {
        WeaponLuaAnimationStateMachineRuntime.clear()
    }

    @Test
    public fun `tick should return null when display definition has no script content`() {
        val tick = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition(
                gunId = "ak47",
                script = null
            ),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(),
            nowMillis = 1_000L
        )

        assertNull(tick)
    }

    @Test
    public fun `tick should reuse session machine and preserve active state across ticks`() {
        val script = """
            states = {
              idle = {
                transition = function(ctx)
                  if ctx.signal_fire then return "fire" end
                  return nil
                end
              },
              fire = {
                transition = function(ctx)
                  if ctx.signal_reload_complete then return "idle" end
                  return nil
                end
              }
            }

            function initialize(ctx)
              return "idle"
            end
        """.trimIndent()

        val first = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", script),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.FIRE)),
            nowMillis = 1_000L
        )
        assertNotNull(first)
        assertFalse(first?.failed == true)
        assertEquals(setOf("fire"), first?.activeStates)

        val second = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", script),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = emptySet()),
            nowMillis = 1_050L
        )

        assertNotNull(second)
        assertFalse(second?.failed == true)
        assertEquals(setOf("fire"), second?.activeStates)
    }

    @Test
    public fun `tick should recreate machine when script content changes for same session`() {
        val scriptA = """
            states = { alpha = {} }
            function initialize(ctx)
              return "alpha"
            end
        """.trimIndent()
        val scriptB = """
            states = { beta = {} }
            function initialize(ctx)
              return "beta"
            end
        """.trimIndent()

        val first = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", scriptA),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(),
            nowMillis = 2_000L
        )
        assertEquals(setOf("alpha"), first?.activeStates)

        val second = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", scriptB),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(),
            nowMillis = 2_050L
        )
        assertEquals(setOf("beta"), second?.activeStates)
    }

    @Test
    public fun `tick should normalize script params into both direct and param-prefixed lua keys`() {
        val script = """
            states = {
              idle = {
                transition = function(ctx)
                  if ctx.param_bolt_feed_time and ctx.param_bolt_feed_time > 0.1 then
                    return "boost"
                  end
                  if ctx.bolt_feed_time and ctx.bolt_feed_time > 0.1 then
                    return "boost_direct"
                  end
                  return nil
                end
              },
              boost = {},
              boost_direct = {}
            }

            function initialize(ctx)
              return "idle"
            end
        """.trimIndent()

        val tick = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "m700",
            displayDefinition = displayDefinition("m700", script),
            gunScriptParams = mapOf("Bolt Feed-Time" to 0.2f),
            behaviorResult = behaviorResult(),
            nowMillis = 3_000L
        )

        assertNotNull(tick)
        assertFalse(tick?.failed == true)
        assertEquals(setOf("boost"), tick?.activeStates)
    }

    @Test
    public fun `tick should clear stale session machine when script becomes unavailable`() {
        val stickyScript = """
            states = {
              idle = {
                transition = function(ctx)
                  if ctx.signal_fire then
                    return "fire"
                  end
                  return nil
                end
              },
              fire = {
                transition = function(ctx)
                  return nil
                end
              }
            }

            function initialize(ctx)
              return "idle"
            end
        """.trimIndent()

        val first = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", stickyScript),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.FIRE)),
            nowMillis = 4_000L
        )
        assertEquals(setOf("fire"), first?.activeStates)

        val missing = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", null),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(),
            nowMillis = 4_050L
        )
        assertNull(missing)

        val rebuilt = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", stickyScript),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = emptySet()),
            nowMillis = 4_100L
        )

        assertNotNull(rebuilt)
        assertEquals(setOf("idle"), rebuilt?.activeStates)
    }

    @Test
    public fun `tick should expose TACZ style input token aliases`() {
        val script = """
            states = {
              idle = {
                transition = function(ctx)
                  if ctx.input == 'reload' and ctx.input_reload == true then
                    return 'reload_token_ok'
                  end
                  return nil
                end
              },
              reload_token_ok = {}
            }

            function initialize(ctx)
              return 'idle'
            end
        """.trimIndent()

        val tick = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", script),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.RELOAD_START)),
            nowMillis = 5_000L
        )

        assertNotNull(tick)
        assertFalse(tick?.failed == true)
        assertEquals(setOf("reload_token_ok"), tick?.activeStates)
    }

    @Test
    public fun `tick should support TACZ module protocol scripts returning module table`() {
        val script = """
            local M = {
              main_track_states = {
                idle = {},
                fire = {}
              }
            }

            function M:states()
              return { self.main_track_states.idle }
            end

            function M.main_track_states.idle.transition(this, context, input)
              if input == 'shoot' and context.input_shoot == true then
                return this.main_track_states.fire
              end
              return nil
            end

            function M.main_track_states.fire.transition(this, context, input)
              if input == 'idle' then
                return this.main_track_states.idle
              end
              return nil
            end

            return M
        """.trimIndent()

        val fireTick = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", script),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.FIRE)),
            nowMillis = 6_000L
        )

        assertNotNull(fireTick)
        assertFalse(fireTick?.failed == true)
        assertEquals(setOf("main_track_states.fire"), fireTick?.activeStates)

        val idleTick = WeaponLuaAnimationStateMachineRuntime.tick(
            sessionId = "player:test:client",
            gunId = "ak47",
            displayDefinition = displayDefinition("ak47", script),
            gunScriptParams = emptyMap(),
            behaviorResult = behaviorResult(signals = emptySet()),
            nowMillis = 6_050L
        )

        assertNotNull(idleTick)
        assertFalse(idleTick?.failed == true)
        assertEquals(setOf("main_track_states.idle"), idleTick?.activeStates)
    }

    private fun displayDefinition(gunId: String, script: String?): GunDisplayDefinition {
        return GunDisplayDefinition(
            sourceId = "sample_pack/assets/tacz/display/guns/$gunId.json",
            gunId = gunId,
            displayResource = "tacz:$gunId",
            modelPath = null,
            modelTexturePath = null,
            lodModelPath = null,
            lodTexturePath = null,
            slotTexturePath = null,
            animationPath = null,
            stateMachinePath = "assets/tacz/scripts/${gunId}_state_machine.lua",
            stateMachineScriptContent = script,
            playerAnimator3rdPath = null,
            thirdPersonAnimation = null,
            modelParseSucceeded = false,
            modelBoneCount = null,
            modelCubeCount = null,
            animationParseSucceeded = false,
            animationClipCount = null,
            stateMachineResolved = !script.isNullOrBlank(),
            playerAnimatorResolved = false,
            hudTexturePath = null,
            hudEmptyTexturePath = null,
            showCrosshair = true
        )
    }

    private fun behaviorResult(signals: Set<WeaponAnimationSignal> = emptySet()): WeaponBehaviorResult {
        return WeaponBehaviorResult(
            step = WeaponStepResult(
                snapshot = WeaponSnapshot(
                    state = if (signals.contains(WeaponAnimationSignal.RELOAD_START)) WeaponState.RELOADING else WeaponState.IDLE,
                    ammoInMagazine = 30,
                    ammoReserve = 90,
                    cooldownTicksRemaining = 0,
                    reloadTicksRemaining = if (signals.contains(WeaponAnimationSignal.RELOAD_START)) 20 else 0,
                    totalShotsFired = if (signals.contains(WeaponAnimationSignal.FIRE)) 1 else 0
                ),
                shotFired = signals.contains(WeaponAnimationSignal.FIRE),
                dryFired = signals.contains(WeaponAnimationSignal.DRY_FIRE),
                reloadStarted = signals.contains(WeaponAnimationSignal.RELOAD_START),
                reloadCompleted = signals.contains(WeaponAnimationSignal.RELOAD_COMPLETE)
            ),
            animationSignals = signals
        )
    }
}

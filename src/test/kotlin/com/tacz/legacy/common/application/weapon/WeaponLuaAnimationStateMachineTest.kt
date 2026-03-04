package com.tacz.legacy.common.application.weapon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponLuaAnimationStateMachineTest {

    @Test
    public fun `state machine should transition across idle fire recoil flow`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:state_machine",
            scriptSource = """
                states = {
                  idle = {
                    transition = function(ctx)
                      if ctx.fire then return "fire" end
                      return nil
                    end
                  },
                  fire = {
                    transition = function(ctx)
                      if ctx.cooldown and ctx.cooldown <= 0 then return { "idle", "recoil" } end
                      return nil
                    end
                  },
                  recoil = {
                    transition = function(ctx)
                      if ctx.reset then return "idle" end
                      return nil
                    end
                  }
                }

                function initialize(ctx)
                  return "idle"
                end
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("idle"), init.activeStates)

        val fireTick = machine.tick(mapOf("fire" to true))
        assertFalse(fireTick.failed)
        assertEquals(setOf("fire"), fireTick.activeStates)

        val recoilTick = machine.tick(mapOf("cooldown" to 0))
        assertFalse(recoilTick.failed)
        assertEquals(setOf("idle", "recoil"), recoilTick.activeStates)

        val settleTick = machine.tick(mapOf("reset" to true))
        assertFalse(settleTick.failed)
        assertEquals(setOf("idle"), settleTick.activeStates)
    }

    @Test
    public fun `state machine should fail safely when lua transition throws`() {
        val machine = WeaponLuaAnimationStateMachine(
            scriptId = "test:state_machine_error",
            scriptSource = """
                states = {
                  idle = {
                    transition = function(ctx)
                      error("boom")
                    end
                  }
                }

                function initialize(ctx)
                  return "idle"
                end
            """.trimIndent()
        )

        val init = machine.initialize()
        assertFalse(init.failed)
        assertEquals(setOf("idle"), init.activeStates)

        val tick = machine.tick()
        assertTrue(tick.failed)
        assertEquals(setOf("idle"), tick.activeStates)
        assertFalse(machine.isAvailable())
        assertNotNull(machine.lastFailureMessage())
    }
}

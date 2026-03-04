package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import com.tacz.legacy.common.application.weapon.WeaponAutoSessionOrchestrator
import com.tacz.legacy.common.application.weapon.WeaponAnimationSignal
import com.tacz.legacy.common.application.weapon.WeaponBehaviorResult
import com.tacz.legacy.common.application.weapon.WeaponLuaAnimationStateMachineRuntime
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.application.weapon.WeaponSessionService
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponStepResult
import com.tacz.legacy.common.domain.weapon.WeaponState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class WeaponPlayerTickEventHandlerTest {

    private val handler = WeaponPlayerTickEventHandler(
        orchestrator = WeaponAutoSessionOrchestrator(
            sessionService = WeaponSessionService(
                runtimeRegistry = com.tacz.legacy.common.application.weapon.WeaponRuntimeRegistry(),
                behaviorEngine = com.tacz.legacy.common.application.weapon.WeaponPortBehaviorEngine(
                    world = object : com.tacz.legacy.common.application.port.WorldPort {
                        override fun raycast(query: com.tacz.legacy.common.application.port.RaycastQuery): com.tacz.legacy.common.application.port.RaycastHit =
                            com.tacz.legacy.common.application.port.RaycastHit(kind = com.tacz.legacy.common.application.port.HitKind.MISS)

                        override fun createBullet(request: com.tacz.legacy.common.application.port.BulletCreationRequest): Int? =
                            null

                        override fun blockStateAt(position: com.tacz.legacy.common.application.port.Vec3i): com.tacz.legacy.common.application.port.BlockStateRef? =
                            null

                        override fun isClientSide(): Boolean = true

                        override fun dimensionKey(): String = "minecraft:overworld"
                    },
                    audio = object : com.tacz.legacy.common.application.port.AudioPort {
                        override fun play(request: com.tacz.legacy.common.application.port.SoundRequest) = Unit
                    },
                    particles = object : com.tacz.legacy.common.application.port.ParticlePort {
                        override fun spawn(request: com.tacz.legacy.common.application.port.ParticleRequest) = Unit
                    }
                )
            )
        ),
        context = WeaponMcExecutionContext()
    )

    @Test
    public fun `primary trigger should be handled by client key handler instead of click events`() {
        assertFalse(handler.shouldHandlePrimaryTrigger(worldIsRemote = true))
        assertFalse(handler.shouldHandlePrimaryTrigger(worldIsRemote = false))
    }

    @Test
    public fun `reload should require client side and sneaking`() {
        assertTrue(handler.shouldHandleReload(worldIsRemote = true, isSneaking = true))
        assertFalse(handler.shouldHandleReload(worldIsRemote = true, isSneaking = false))
        assertFalse(handler.shouldHandleReload(worldIsRemote = false, isSneaking = true))
        assertFalse(handler.shouldHandleReload(worldIsRemote = false, isSneaking = false))
    }

    @Test
    public fun `authoritative sync should emit on first time changes or resend interval`() {
        assertTrue(
            handler.shouldEmitAuthoritativeSync(
                lastSignature = null,
                currentSignature = 10,
                lastSyncedTick = null,
                currentTick = 100
            )
        )

        assertTrue(
            handler.shouldEmitAuthoritativeSync(
                lastSignature = 10,
                currentSignature = 11,
                lastSyncedTick = 100,
                currentTick = 101
            )
        )

        assertFalse(
            handler.shouldEmitAuthoritativeSync(
                lastSignature = 10,
                currentSignature = 10,
                lastSyncedTick = 100,
                currentTick = 110,
                intervalTicks = 20
            )
        )

        assertTrue(
            handler.shouldEmitAuthoritativeSync(
                lastSignature = 10,
                currentSignature = 10,
                lastSyncedTick = 100,
                currentTick = 120,
                intervalTicks = 20
            )
        )
    }

    @Test
    public fun `sync signature should stay stable for same snapshot and differ when key fields change`() {
        val base = WeaponSessionDebugSnapshot(
            sourceId = "sample_pack/data/tacz/data/guns/rifle/ak47.json",
            gunId = "ak47",
            snapshot = WeaponSnapshot(
                state = WeaponState.IDLE,
                ammoInMagazine = 30,
                ammoReserve = 90,
                cooldownTicksRemaining = 0,
                reloadTicksRemaining = 0,
                totalShotsFired = 0
            )
        )

        val same = base.copy()
        val changed = base.copy(
            snapshot = base.snapshot.copy(ammoInMagazine = 29)
        )

        val baseSig = handler.buildSyncSignature(base)
        val sameSig = handler.buildSyncSignature(same)
        val changedSig = handler.buildSyncSignature(changed)

        assertEquals(baseSig, sameSig)
        assertNotEquals(baseSig, changedSig)
    }

    @Test
    public fun `manual action display with bolt clip should prefer fire to bolt transition`() {
        val display = displayDefinition(
            stateMachinePath = "assets/tacz/states/manual_action_state_machine.lua",
            animationBoltClipName = "pull_bolt"
        )

        assertTrue(handler.shouldPreferBoltCycleAfterFire(display))
    }

    @Test
    public fun `bolt shell timing param should enable fire to bolt transition when bolt clip can be inferred`() {
        val display = displayDefinition(
            stateMachinePath = "assets/tacz/states/default_state_machine.lua",
            stateMachineParams = mapOf("bolt_shell_ejecting_time" to 0.12f),
            animationClipNames = listOf("idle", "pull_bolt")
        )

        assertTrue(handler.shouldPreferBoltCycleAfterFire(display))
    }

    @Test
    public fun `manual action display without bolt clip should not prefer fire to bolt transition`() {
        val display = displayDefinition(
            stateMachinePath = "assets/tacz/states/manual_action_state_machine.lua",
            animationBoltClipName = null,
            animationClipNames = listOf("idle", "shoot")
        )

        assertFalse(handler.shouldPreferBoltCycleAfterFire(display))
    }

    @Test
    public fun `default state machine with bolt clip but no timing param should not force bolt transition`() {
        val display = displayDefinition(
            stateMachinePath = "assets/tacz/states/default_state_machine.lua",
            animationBoltClipName = "pull_bolt"
        )

        assertFalse(handler.shouldPreferBoltCycleAfterFire(display))
    }

    @Test
    public fun `gun script params should enable bolt transition when display has bolt clip`() {
        val display = displayDefinition(
            stateMachinePath = "assets/tacz/states/default_state_machine.lua",
            animationBoltClipName = "pull_bolt"
        )

        assertTrue(
            handler.shouldPreferBoltCycleAfterFire(
                displayDefinition = display,
                gunScriptParams = mapOf("bolt_feed_time" to 0.12f)
            )
        )
    }

    @Test
    public fun `animation clip source should stay signal when lua feature is disabled`() {
        WeaponLuaAnimationStateMachineRuntime.clear()
        try {
            val source = handler.resolveAnimationClipSource(
                worldIsRemote = true,
                sessionId = "player:test:client",
                gunId = "ak47",
                displayDefinition = displayDefinition(
                    stateMachinePath = "assets/tacz/scripts/ak47_state_machine.lua",
                    stateMachineScriptContent = "states={ idle={} }; function initialize(ctx) return 'idle' end"
                ),
                gunScriptParams = emptyMap(),
                behaviorResult = behaviorResult(),
                luaFeatureEnabled = false
            )

            assertEquals(WeaponAnimationClipSource.SIGNAL, source)
        } finally {
            WeaponLuaAnimationStateMachineRuntime.clear()
        }
    }

    @Test
    public fun `animation clip source should fallback when lua is enabled but script is unavailable`() {
        WeaponLuaAnimationStateMachineRuntime.clear()
        try {
            val source = handler.resolveAnimationClipSource(
                worldIsRemote = true,
                sessionId = "player:test:client",
                gunId = "ak47",
                displayDefinition = displayDefinition(
                    stateMachinePath = "assets/tacz/scripts/ak47_state_machine.lua",
                    stateMachineScriptContent = null
                ),
                gunScriptParams = emptyMap(),
                behaviorResult = behaviorResult(),
                luaFeatureEnabled = true
            )

            assertEquals(WeaponAnimationClipSource.SIGNAL_FALLBACK, source)
        } finally {
            WeaponLuaAnimationStateMachineRuntime.clear()
        }
    }

    @Test
    public fun `animation clip source should switch to lua when lua state machine tick succeeds`() {
        WeaponLuaAnimationStateMachineRuntime.clear()
        try {
            val source = handler.resolveAnimationClipSource(
                worldIsRemote = true,
                sessionId = "player:test:client",
                gunId = "ak47",
                displayDefinition = displayDefinition(
                    stateMachinePath = "assets/tacz/scripts/ak47_state_machine.lua",
                    stateMachineScriptContent = """
                        states = {
                          idle = {
                            transition = function(ctx)
                              if ctx.fire then return 'fire' end
                              return nil
                            end
                          },
                          fire = {
                            transition = function(ctx)
                              if not ctx.fire then return 'idle' end
                              return nil
                            end
                          }
                        }
                        function initialize(ctx)
                          return 'idle'
                        end
                    """.trimIndent()
                ),
                gunScriptParams = mapOf("bolt_feed_time" to 0.15f),
                behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.FIRE)),
                luaFeatureEnabled = true
            )

            assertEquals(WeaponAnimationClipSource.LUA_STATE_MACHINE, source)
        } finally {
            WeaponLuaAnimationStateMachineRuntime.clear()
        }
    }

    @Test
    public fun `animation runtime path should expose preferred clip resolved from lua active state`() {
        WeaponLuaAnimationStateMachineRuntime.clear()
        try {
            val path = handler.resolveAnimationRuntimePath(
                worldIsRemote = true,
                sessionId = "player:test:client",
                gunId = "ak47",
                displayDefinition = displayDefinition(
                    stateMachinePath = "assets/tacz/scripts/ak47_state_machine.lua",
                    stateMachineScriptContent = """
                        states = {
                          idle = {
                            transition = function(ctx)
                              if ctx.fire then return 'aiming_hold' end
                              return nil
                            end
                          },
                          aiming_hold = {}
                        }
                        function initialize(ctx)
                          return 'idle'
                        end
                    """.trimIndent()
                ),
                gunScriptParams = emptyMap(),
                behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.FIRE)),
                luaFeatureEnabled = true
            )

            assertEquals(WeaponAnimationClipSource.LUA_STATE_MACHINE, path.clipSource)
            assertEquals(WeaponAnimationClipType.AIM, path.preferredClip)
        } finally {
            WeaponLuaAnimationStateMachineRuntime.clear()
        }
    }

        @Test
        public fun `animation runtime path should stay on lua source when script requires bundled default module`() {
                WeaponLuaAnimationStateMachineRuntime.clear()
                try {
                        val path = handler.resolveAnimationRuntimePath(
                                worldIsRemote = true,
                                sessionId = "player:test:client",
                                gunId = "ak47",
                                displayDefinition = displayDefinition(
                                        stateMachinePath = "assets/tacz/scripts/ak47_state_machine.lua",
                                        stateMachineScriptContent = """
                                                local default = require("tacz_default_state_machine")

                                                local M = {
                                                    flow = {
                                                        idle = {
                                                            transition = function(this, context, input)
                                                                if input == 'shoot' and default.main_track_states ~= nil then
                                                                    return this.flow.fire
                                                                end
                                                                return nil
                                                            end
                                                        },
                                                        fire = {}
                                                    }
                                                }

                                                function M:states()
                                                    return { self.flow.idle }
                                                end

                                                return M
                                        """.trimIndent()
                                ),
                                gunScriptParams = emptyMap(),
                                behaviorResult = behaviorResult(signals = setOf(WeaponAnimationSignal.FIRE)),
                                luaFeatureEnabled = true
                        )

                        assertEquals(WeaponAnimationClipSource.LUA_STATE_MACHINE, path.clipSource)
                        assertEquals(WeaponAnimationClipType.FIRE, path.preferredClip)
                } finally {
                        WeaponLuaAnimationStateMachineRuntime.clear()
                }
        }

    @Test
    public fun `lua preferred clip resolver should pick highest priority semantic clip`() {
        val resolved = handler.resolveLuaPreferredClip(
            setOf("idle", "run_loop", "reload_tactical")
        )

        assertEquals(WeaponAnimationClipType.RELOAD, resolved)
    }

    @Test
    public fun `lua preferred clip resolver should return null when no known semantic state exists`() {
        val resolved = handler.resolveLuaPreferredClip(
            setOf("foo_state", "bar_mode")
        )

        assertTrue(resolved == null)
    }

    @Test
    public fun `lua preferred clip resolver should map walk aiming state to walk clip`() {
        val resolved = handler.resolveLuaPreferredClip(
            setOf("walk_aiming")
        )

        assertEquals(WeaponAnimationClipType.WALK, resolved)
    }

    @Test
    public fun `lua preferred clip resolver should not treat fire select as fire recoil`() {
        val resolved = handler.resolveLuaPreferredClip(
            setOf("fire_select")
        )

        assertTrue(resolved == null)
    }

    @Test
    public fun `lua preferred clip resolver should map final state to put away clip`() {
        val resolved = handler.resolveLuaPreferredClip(
            setOf("final")
        )

        assertEquals(WeaponAnimationClipType.PUT_AWAY, resolved)
    }

    @Test
    public fun `lua preferred clip resolver should map main track start state to draw clip`() {
        val resolved = handler.resolveLuaPreferredClip(
            setOf("main_track_states.start")
        )

        assertEquals(WeaponAnimationClipType.DRAW, resolved)
    }

    private fun displayDefinition(
        stateMachinePath: String,
        stateMachineParams: Map<String, Float> = emptyMap(),
        animationBoltClipName: String? = null,
        animationClipNames: List<String>? = null,
        stateMachineScriptContent: String? = "return {}"
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
        stateMachineScriptContent = stateMachineScriptContent,
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

    private fun behaviorResult(signals: Set<WeaponAnimationSignal> = emptySet()): WeaponBehaviorResult =
        WeaponBehaviorResult(
            step = WeaponStepResult(
                snapshot = WeaponSnapshot(
                    state = WeaponState.IDLE,
                    ammoInMagazine = 30,
                    ammoReserve = 90,
                    cooldownTicksRemaining = 0,
                    reloadTicksRemaining = 0,
                    totalShotsFired = 0
                ),
                shotFired = signals.contains(WeaponAnimationSignal.FIRE),
                dryFired = signals.contains(WeaponAnimationSignal.DRY_FIRE),
                reloadStarted = signals.contains(WeaponAnimationSignal.RELOAD_START),
                reloadCompleted = signals.contains(WeaponAnimationSignal.RELOAD_COMPLETE)
            ),
            animationSignals = signals
        )

}

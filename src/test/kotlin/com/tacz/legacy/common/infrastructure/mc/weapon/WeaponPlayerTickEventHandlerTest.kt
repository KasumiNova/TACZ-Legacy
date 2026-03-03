package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.weapon.WeaponAutoSessionOrchestrator
import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.application.weapon.WeaponSessionService
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
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

}

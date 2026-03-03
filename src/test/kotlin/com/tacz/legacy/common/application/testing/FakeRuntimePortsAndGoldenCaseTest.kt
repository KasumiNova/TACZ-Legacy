package com.tacz.legacy.common.application.testing

import com.tacz.legacy.common.application.port.EntitySnapshot
import com.tacz.legacy.common.application.port.ParticleRequest
import com.tacz.legacy.common.application.port.SoundRequest
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.port.Vec3i
import com.tacz.legacy.common.application.port.BlockStateRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class FakeRuntimePortsAndGoldenCaseTest {

    @Test
    public fun `fake fixture should record audio and particles`() {
        val fixture = RuntimePortsFixtures.create(seed = 7L)

        fixture.audio.play(SoundRequest("tacz:test_sound", Vec3d(1.0, 2.0, 3.0)))
        fixture.particles.spawn(ParticleRequest("smoke", Vec3d(4.0, 5.0, 6.0)))

        assertEquals(1, fixture.audio.recorded().size)
        assertEquals(1, fixture.particles.recorded().size)
        assertEquals("tacz:test_sound", fixture.audio.recorded().first().soundId)
        assertEquals("smoke", fixture.particles.recorded().first().particleType)
    }

    @Test
    public fun `fake world should expose configured block state`() {
        val world = FakeWorldPort()
        val pos = Vec3i(10, 64, -2)
        world.setBlockState(pos, BlockStateRef("minecraft:stone", metadata = 1))

        val block = world.blockStateAt(pos)
        assertEquals("minecraft:stone", block?.blockId)
        assertEquals(1, block?.metadata)
    }

    @Test
    public fun `fake entity port should query nearby snapshots`() {
        val entities = FakeEntityPort()
        entities.put(
            EntitySnapshot(
                entityId = 1,
                entityType = "minecraft:zombie",
                position = Vec3d(0.0, 64.0, 0.0),
                velocity = Vec3d.ZERO,
                health = 20.0f,
                onGround = true,
                sneaking = false
            )
        )
        entities.put(
            EntitySnapshot(
                entityId = 2,
                entityType = "minecraft:skeleton",
                position = Vec3d(50.0, 64.0, 50.0),
                velocity = Vec3d.ZERO,
                health = 20.0f,
                onGround = true,
                sneaking = false
            )
        )

        val nearby = entities.nearby(center = Vec3d(0.0, 64.0, 0.0), radius = 10.0)
        assertEquals(1, nearby.size)
        assertEquals(1, nearby.first().entityId)
    }

    @Test
    public fun `golden case runner should assert deterministic outputs`() {
        val runner = GoldenCaseRunner<Int, Int>()
        val cases = listOf(
            GoldenCase("double-1", input = 1, expected = 2),
            GoldenCase("double-2", input = 2, expected = 4),
            GoldenCase("double-3", input = 3, expected = 6)
        )

        val results = runner.run(cases) { input -> input * 2 }
        assertTrue(results.all { it.passed })

        runner.assertAll(cases) { input -> input * 2 }
    }

}

package com.tacz.legacy.common.application.port

import java.util.Random

public data class Vec3d(
    val x: Double,
    val y: Double,
    val z: Double
) {

    public companion object {
        public val ZERO: Vec3d = Vec3d(0.0, 0.0, 0.0)
    }

}

public data class Vec3i(
    val x: Int,
    val y: Int,
    val z: Int
)

public data class BlockStateRef(
    val blockId: String,
    val metadata: Int? = null
)

public enum class HitKind {
    MISS,
    BLOCK,
    ENTITY
}

public data class RaycastQuery(
    val origin: Vec3d,
    val direction: Vec3d,
    val maxDistance: Double,
    val includeFluids: Boolean = false
)

public data class RaycastHit(
    val kind: HitKind,
    val position: Vec3d? = null,
    val blockState: BlockStateRef? = null,
    val entityId: Int? = null
)

public data class EntitySnapshot(
    val entityId: Int,
    val entityType: String,
    val position: Vec3d,
    val velocity: Vec3d,
    val health: Float,
    val onGround: Boolean,
    val sneaking: Boolean
)

public data class SoundRequest(
    val soundId: String,
    val position: Vec3d,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    val category: String = "master"
)

public data class ParticleRequest(
    val particleType: String,
    val position: Vec3d,
    val velocity: Vec3d = Vec3d.ZERO,
    val count: Int = 1
)

public data class BulletCreationRequest(
    val ownerEntityId: Int? = null,
    val origin: Vec3d,
    val direction: Vec3d,
    val speed: Float,
    val gravity: Float,
    val friction: Float = 0.01f,
    val damage: Float,
    val maxLifetimeTicks: Int,
    val pierce: Int = 1,
    val inaccuracyDegrees: Float = 0.0f
)

public interface WorldPort {

    public fun raycast(query: RaycastQuery): RaycastHit

    public fun createBullet(request: BulletCreationRequest): Int?

    public fun blockStateAt(position: Vec3i): BlockStateRef?

    public fun isClientSide(): Boolean

    public fun dimensionKey(): String

}

public interface EntityPort {

    public fun self(): EntitySnapshot?

    public fun byId(entityId: Int): EntitySnapshot?

    public fun nearby(center: Vec3d, radius: Double): List<EntitySnapshot>

}

public interface AudioPort {

    public fun play(request: SoundRequest)

}

public interface ParticlePort {

    public fun spawn(request: ParticleRequest)

}

public interface TimePort {

    public fun gameTimeTicks(): Long

    public fun partialTicks(): Float

    public fun deltaSeconds(): Float

}

public interface RandomPort {

    public fun nextInt(bound: Int): Int

    public fun nextFloat(): Float

    public fun nextDouble(): Double

    public fun nextBoolean(): Boolean

}

public class SeededRandomPort(seed: Long) : RandomPort {

    private val random: Random = Random(seed)

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be > 0, got $bound" }
        return random.nextInt(bound)
    }

    override fun nextFloat(): Float = random.nextFloat()

    override fun nextDouble(): Double = random.nextDouble()

    override fun nextBoolean(): Boolean = random.nextBoolean()

}

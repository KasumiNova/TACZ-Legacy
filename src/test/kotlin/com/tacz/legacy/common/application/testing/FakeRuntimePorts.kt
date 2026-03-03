package com.tacz.legacy.common.application.testing

import com.tacz.legacy.common.application.port.AudioPort
import com.tacz.legacy.common.application.port.BlockStateRef
import com.tacz.legacy.common.application.port.BulletCreationRequest
import com.tacz.legacy.common.application.port.EntityPort
import com.tacz.legacy.common.application.port.EntitySnapshot
import com.tacz.legacy.common.application.port.HitKind
import com.tacz.legacy.common.application.port.ParticlePort
import com.tacz.legacy.common.application.port.ParticleRequest
import com.tacz.legacy.common.application.port.RandomPort
import com.tacz.legacy.common.application.port.RaycastHit
import com.tacz.legacy.common.application.port.RaycastQuery
import com.tacz.legacy.common.application.port.SeededRandomPort
import com.tacz.legacy.common.application.port.SoundRequest
import com.tacz.legacy.common.application.port.TimePort
import com.tacz.legacy.common.application.port.Vec3d
import com.tacz.legacy.common.application.port.Vec3i
import com.tacz.legacy.common.application.port.WorldPort

public class FakeWorldPort(
    private var clientSide: Boolean = false,
    private var dimension: String = "minecraft:overworld"
) : WorldPort {

    private val blockStates: MutableMap<Vec3i, BlockStateRef> = mutableMapOf()
    private val createdBullets: MutableList<BulletCreationRequest> = mutableListOf()
    private var nextBulletEntityId: Int = 1
    private var nextRaycastHit: RaycastHit = RaycastHit(kind = HitKind.MISS)

    override fun raycast(query: RaycastQuery): RaycastHit = nextRaycastHit

    override fun createBullet(request: BulletCreationRequest): Int? {
        createdBullets += request
        val assignedId = nextBulletEntityId
        nextBulletEntityId += 1
        return assignedId
    }

    override fun blockStateAt(position: Vec3i): BlockStateRef? = blockStates[position]

    override fun isClientSide(): Boolean = clientSide

    override fun dimensionKey(): String = dimension

    public fun setRaycastHit(hit: RaycastHit) {
        nextRaycastHit = hit
    }

    public fun setBlockState(position: Vec3i, blockState: BlockStateRef) {
        blockStates[position] = blockState
    }

    public fun setClientSide(value: Boolean) {
        clientSide = value
    }

    public fun setDimension(value: String) {
        dimension = value
    }

    public fun recordedBullets(): List<BulletCreationRequest> = createdBullets.toList()

    public fun clearRecordedBullets() {
        createdBullets.clear()
        nextBulletEntityId = 1
    }

}

public class FakeEntityPort(
    private var selfSnapshot: EntitySnapshot? = null
) : EntityPort {

    private val entitiesById: MutableMap<Int, EntitySnapshot> = linkedMapOf()

    override fun self(): EntitySnapshot? = selfSnapshot

    override fun byId(entityId: Int): EntitySnapshot? = entitiesById[entityId]

    override fun nearby(center: Vec3d, radius: Double): List<EntitySnapshot> {
        val squared = radius * radius
        return entitiesById.values.filter { snapshot ->
            val dx = snapshot.position.x - center.x
            val dy = snapshot.position.y - center.y
            val dz = snapshot.position.z - center.z
            dx * dx + dy * dy + dz * dz <= squared
        }
    }

    public fun setSelf(snapshot: EntitySnapshot?) {
        selfSnapshot = snapshot
    }

    public fun put(snapshot: EntitySnapshot) {
        entitiesById[snapshot.entityId] = snapshot
    }

}

public class RecordingAudioPort : AudioPort {

    private val requests: MutableList<SoundRequest> = mutableListOf()

    override fun play(request: SoundRequest) {
        requests += request
    }

    public fun recorded(): List<SoundRequest> = requests.toList()

    public fun clear() {
        requests.clear()
    }

}

public class RecordingParticlePort : ParticlePort {

    private val requests: MutableList<ParticleRequest> = mutableListOf()

    override fun spawn(request: ParticleRequest) {
        requests += request
    }

    public fun recorded(): List<ParticleRequest> = requests.toList()

    public fun clear() {
        requests.clear()
    }

}

public class FakeTimePort(
    private var ticks: Long = 0L,
    private var partial: Float = 0.0f,
    private var delta: Float = 0.05f
) : TimePort {

    override fun gameTimeTicks(): Long = ticks

    override fun partialTicks(): Float = partial

    override fun deltaSeconds(): Float = delta

    public fun advance(byTicks: Long = 1L) {
        ticks += byTicks
    }

    public fun setPartialTicks(value: Float) {
        partial = value
    }

    public fun setDeltaSeconds(value: Float) {
        delta = value
    }

}

public data class RuntimePortsFixture(
    val world: FakeWorldPort,
    val entities: FakeEntityPort,
    val audio: RecordingAudioPort,
    val particles: RecordingParticlePort,
    val time: FakeTimePort,
    val random: RandomPort
)

public object RuntimePortsFixtures {

    public fun create(seed: Long = 12345L): RuntimePortsFixture =
        RuntimePortsFixture(
            world = FakeWorldPort(),
            entities = FakeEntityPort(),
            audio = RecordingAudioPort(),
            particles = RecordingParticlePort(),
            time = FakeTimePort(),
            random = SeededRandomPort(seed)
        )

}

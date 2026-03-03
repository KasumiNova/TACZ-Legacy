package com.tacz.legacy.common.infrastructure.mc.weapon

import com.tacz.legacy.common.application.port.AudioPort
import com.tacz.legacy.common.application.port.BlockStateRef
import com.tacz.legacy.common.application.port.BulletCreationRequest
import com.tacz.legacy.common.application.port.HitKind
import com.tacz.legacy.common.application.port.ParticlePort
import com.tacz.legacy.common.application.port.ParticleRequest
import com.tacz.legacy.common.application.port.RaycastHit
import com.tacz.legacy.common.application.port.RaycastQuery
import com.tacz.legacy.common.application.port.SoundRequest
import com.tacz.legacy.common.application.port.Vec3i
import com.tacz.legacy.common.application.port.WorldPort
import com.tacz.legacy.common.infrastructure.mc.entity.LegacyBulletEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.SoundEvent
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult.Type
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d
import net.minecraft.world.WorldServer
import kotlin.math.sqrt

public class WeaponMcExecutionContext {

    private val currentPlayer: ThreadLocal<EntityPlayer?> = ThreadLocal()

    public fun <T> withPlayer(player: EntityPlayer, block: () -> T): T {
        currentPlayer.set(player)
        return try {
            block()
        } finally {
            currentPlayer.remove()
        }
    }

    public fun currentPlayerOrNull(): EntityPlayer? = currentPlayer.get()

}

internal class MinecraftWorldPort(
    private val context: WeaponMcExecutionContext
) : WorldPort {

    override fun raycast(query: RaycastQuery): RaycastHit {
        val player = context.currentPlayerOrNull() ?: return RaycastHit(kind = HitKind.MISS)
        val world = player.world

        val origin = Vec3d(query.origin.x, query.origin.y, query.origin.z)
        val end = Vec3d(
            query.origin.x + query.direction.x * query.maxDistance,
            query.origin.y + query.direction.y * query.maxDistance,
            query.origin.z + query.direction.z * query.maxDistance
        )

        val blockResult = world.rayTraceBlocks(
            origin,
            end,
            query.includeFluids,
            !query.includeFluids,
            false
        )

        val entityResult = findNearestEntityHit(player, origin, end)

        val blockDistanceSq = blockResult?.hitVec?.squareDistanceTo(origin)
        val entityDistanceSq = entityResult?.hitVec?.squareDistanceTo(origin)

        val result = when {
            entityResult == null -> blockResult
            blockResult == null -> entityResult
            blockDistanceSq == null -> entityResult
            entityDistanceSq == null -> blockResult
            entityDistanceSq <= blockDistanceSq -> entityResult
            else -> blockResult
        } ?: return RaycastHit(kind = HitKind.MISS)

        return when (result.typeOfHit) {
            Type.BLOCK -> {
                val pos = result.blockPos
                val stateRef = pos.let { blockPos ->
                    val state = world.getBlockState(blockPos)
                    val blockId = state.block.registryName?.toString() ?: "minecraft:air"
                    val metadata = runCatching { state.block.getMetaFromState(state) }.getOrNull()
                    BlockStateRef(blockId = blockId, metadata = metadata)
                }

                RaycastHit(
                    kind = HitKind.BLOCK,
                    position = result.hitVec?.let { hit -> com.tacz.legacy.common.application.port.Vec3d(hit.x, hit.y, hit.z) },
                    blockState = stateRef
                )
            }

            Type.ENTITY -> RaycastHit(
                kind = HitKind.ENTITY,
                position = result.hitVec?.let { hit -> com.tacz.legacy.common.application.port.Vec3d(hit.x, hit.y, hit.z) },
                entityId = result.entityHit?.entityId
            )

            else -> RaycastHit(kind = HitKind.MISS)
        }
    }

    private fun findNearestEntityHit(player: EntityPlayer, origin: Vec3d, end: Vec3d): RayTraceResult? {
        val world = player.world
        val sweepBox = AxisAlignedBB(origin.x, origin.y, origin.z, end.x, end.y, end.z).grow(1.0)
        val candidates = world.getEntitiesWithinAABBExcludingEntity(player, sweepBox)

        var bestResult: RayTraceResult? = null
        var bestDistanceSq = Double.MAX_VALUE

        for (entity in candidates) {
            if (entity == null || !entity.canBeCollidedWith()) {
                continue
            }

            val border = entity.collisionBorderSize.toDouble()
            val entityBox = entity.entityBoundingBox.grow(border)
            val intercept = entityBox.calculateIntercept(origin, end) ?: continue
            val distanceSq = origin.squareDistanceTo(intercept.hitVec)

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestResult = RayTraceResult(entity, intercept.hitVec)
            }
        }

        return bestResult
    }

    override fun blockStateAt(position: Vec3i): BlockStateRef? {
        val player = context.currentPlayerOrNull() ?: return null
        val world = player.world
        val pos = BlockPos(position.x, position.y, position.z)
        if (!world.isBlockLoaded(pos)) {
            return null
        }

        val state = world.getBlockState(pos)
        val blockId = state.block.registryName?.toString() ?: return null
        val metadata = runCatching { state.block.getMetaFromState(state) }.getOrNull()
        return BlockStateRef(blockId = blockId, metadata = metadata)
    }

    override fun createBullet(request: BulletCreationRequest): Int? {
        val player = context.currentPlayerOrNull() ?: return null
        val world = player.world
        if (world.isRemote) {
            return null
        }

        val normalizedDirection = normalizeDirection(request.direction)
        val inheritedVelocityX = player.motionX
        val inheritedVelocityY = if (player.onGround) 0.0 else player.motionY
        val inheritedVelocityZ = player.motionZ
        val bullet = LegacyBulletEntity(world, player).apply {
            setPosition(request.origin.x, request.origin.y, request.origin.z)
            configure(
                damage = request.damage,
                gravity = request.gravity,
                friction = request.friction,
                pierce = request.pierce,
                lifetimeTicks = request.maxLifetimeTicks
            )
            shoot(
                normalizedDirection.x,
                normalizedDirection.y,
                normalizedDirection.z,
                request.speed,
                0.0f
            )
            motionX += inheritedVelocityX
            motionY += inheritedVelocityY
            motionZ += inheritedVelocityZ
        }

        return if (world.spawnEntity(bullet)) bullet.entityId else null
    }

    override fun isClientSide(): Boolean = context.currentPlayerOrNull()?.world?.isRemote ?: false

    override fun dimensionKey(): String {
        val player = context.currentPlayerOrNull() ?: return "minecraft:unknown"
        val rawName = player.world.provider.dimensionType.name
            .trim()
            .lowercase()
            .replace(' ', '_')
        return if (':' in rawName) rawName else "minecraft:$rawName"
    }

    private fun normalizeDirection(direction: com.tacz.legacy.common.application.port.Vec3d): com.tacz.legacy.common.application.port.Vec3d {
        val lengthSq = direction.x * direction.x + direction.y * direction.y + direction.z * direction.z
        if (lengthSq <= 1.0e-8) {
            return com.tacz.legacy.common.application.port.Vec3d(0.0, 0.0, 1.0)
        }

        val invLength = 1.0 / sqrt(lengthSq)
        return com.tacz.legacy.common.application.port.Vec3d(
            x = direction.x * invLength,
            y = direction.y * invLength,
            z = direction.z * invLength
        )
    }

}

internal class MinecraftAudioPort(
    private val context: WeaponMcExecutionContext
) : AudioPort {

    override fun play(request: SoundRequest) {
        val player = context.currentPlayerOrNull() ?: return
        val world = player.world

        if (world.isRemote && tryPlayClientRawSound(request)) {
            return
        }

        val soundEvent = SoundEvent.REGISTRY.getObject(ResourceLocation(request.soundId))
            ?: SoundEvents.BLOCK_NOTE_HAT

        world.playSound(
            player,
            request.position.x,
            request.position.y,
            request.position.z,
            soundEvent,
            resolveCategory(request.category),
            request.volume,
            request.pitch
        )
    }

    private fun tryPlayClientRawSound(request: SoundRequest): Boolean {
        return runCatching {
            val bridgeClass = Class.forName("com.tacz.legacy.client.sound.ClientSoundPlaybackBridge")
            val method = bridgeClass.getMethod(
                "tryPlayRawSound",
                String::class.java,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                String::class.java
            )

            method.invoke(
                null,
                request.soundId,
                request.position.x.toFloat(),
                request.position.y.toFloat(),
                request.position.z.toFloat(),
                request.volume,
                request.pitch,
                request.category
            ) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun resolveCategory(raw: String): SoundCategory =
        SoundCategory.values().firstOrNull { category ->
            category.name.equals(raw, ignoreCase = true)
        } ?: SoundCategory.MASTER

}

internal class MinecraftParticlePort(
    private val context: WeaponMcExecutionContext
) : ParticlePort {

    override fun spawn(request: ParticleRequest) {
        val player = context.currentPlayerOrNull() ?: return
        val world = player.world as? WorldServer ?: return
        val type = resolveParticleType(request.particleType)

        world.spawnParticle(
            type,
            request.position.x,
            request.position.y,
            request.position.z,
            request.count,
            request.velocity.x,
            request.velocity.y,
            request.velocity.z,
            0.0
        )
    }

    private fun resolveParticleType(raw: String): EnumParticleTypes {
        val name = raw.substringAfter(':')
        return EnumParticleTypes.getByName(name)
            ?: EnumParticleTypes.getByName(name.lowercase())
            ?: EnumParticleTypes.CRIT
    }

}
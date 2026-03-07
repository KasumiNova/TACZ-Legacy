package com.tacz.legacy.client.audio

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.sound.GunSoundInstance
import com.tacz.legacy.client.sound.GunSoundPlayManager
import net.minecraft.entity.Entity
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

internal object TACZAudioRuntime {
    private const val BACKEND_PROPERTY: String = "tacz.audio.backend"
    private const val PREFLIGHT_PROPERTY: String = "tacz.audio.preflight"
    private const val PREFLIGHT_STRICT_PROPERTY: String = "tacz.audio.preflight.strict"
    private const val FOCUSED_SMOKE_PROPERTY: String = "tacz.focusedSmoke"
    private const val MAX_RECENT_SUBMISSIONS: Int = 64
    private const val MAX_PREFLIGHT_LOGGED_ISSUES: Int = 24

    private val MARKER: Marker = MarkerManager.getMarker("TACZAudio")
    private val focusedSmokeRequestKeys = ConcurrentHashMap.newKeySet<String>()
    private val recentSubmissions = ArrayDeque<TACZAudioSubmissionRecord>()

    @Volatile
    private var manifest: TACZAudioManifest = TACZAudioManifest(emptyMap())

    @JvmStatic
    fun reload(
        packSources: List<File>,
        audioReferences: Map<ResourceLocation, Set<TACZAudioReference>>,
    ) {
        manifest = TACZAudioProbe.buildManifest(packSources, audioReferences)
        synchronized(recentSubmissions) {
            recentSubmissions.clear()
        }
        focusedSmokeRequestKeys.clear()
        logManifestSummary(manifest)
    }

    @JvmStatic
    fun clear() {
        manifest = TACZAudioManifest(emptyMap())
        synchronized(recentSubmissions) {
            recentSubmissions.clear()
        }
        focusedSmokeRequestKeys.clear()
    }

    @JvmStatic
    fun getManifest(): TACZAudioManifest = manifest

    @JvmStatic
    fun getRecentSubmissions(): List<TACZAudioSubmissionRecord> = synchronized(recentSubmissions) {
        ArrayList(recentSubmissions)
    }

    @JvmStatic
    fun shouldUseLegacyMinecraftBridge(): Boolean = resolveBackendMode() == TACZAudioBackendMode.LEGACY_MINECRAFT

    @JvmStatic
    fun play(
        entity: Entity?,
        soundId: ResourceLocation,
        volume: Float,
        pitch: Float,
        distance: Int,
        origin: TACZAudioRequestOrigin,
        legacyBackend: GunSoundPlayManager.SoundPlaybackBackend,
    ): GunSoundInstance? {
        val backendMode = resolveBackendMode()
        val descriptor = manifest.entries[soundId]
        return when (backendMode) {
            TACZAudioBackendMode.LEGACY_MINECRAFT -> {
                val instance = legacyBackend.play(entity, soundId, volume, pitch, distance, origin)
                recordSubmission(
                    TACZAudioSubmissionRecord(
                        origin = origin,
                        soundId = soundId,
                        backendMode = backendMode,
                        probeStatus = descriptor?.probeStatus ?: TACZAudioProbeStatus.UNTRACKED,
                        disposition = if (instance != null) {
                            TACZAudioSubmissionDisposition.SUBMITTED_TO_BACKEND
                        } else {
                            TACZAudioSubmissionDisposition.DROPPED
                        },
                        notes = buildString {
                            append("legacy-backend-submission")
                            descriptor?.notes?.let {
                                append("; ")
                                append(it)
                            }
                        },
                    )
                )
                instance
            }

            TACZAudioBackendMode.NULL -> {
                recordSubmission(
                    TACZAudioSubmissionRecord(
                        origin = origin,
                        soundId = soundId,
                        backendMode = backendMode,
                        probeStatus = descriptor?.probeStatus ?: TACZAudioProbeStatus.UNTRACKED,
                        disposition = TACZAudioSubmissionDisposition.RECORDED_ONLY,
                        notes = "null-backend",
                    )
                )
                null
            }

            TACZAudioBackendMode.DIAGNOSTIC -> {
                recordSubmission(
                    TACZAudioSubmissionRecord(
                        origin = origin,
                        soundId = soundId,
                        backendMode = backendMode,
                        probeStatus = descriptor?.probeStatus ?: TACZAudioProbeStatus.UNTRACKED,
                        disposition = TACZAudioSubmissionDisposition.RECORDED_ONLY,
                        notes = buildString {
                            append("diagnostic-backend")
                            descriptor?.notes?.let {
                                append("; ")
                                append(it)
                            }
                        },
                    )
                )
                null
            }
        }
    }

    private fun resolveBackendMode(): TACZAudioBackendMode =
        TACZAudioBackendMode.fromProperty(System.getProperty(BACKEND_PROPERTY))

    private fun isPreflightEnabled(): Boolean =
        System.getProperty(PREFLIGHT_PROPERTY, if (isFocusedSmokeEnabled()) "true" else "false").toBoolean()

    private fun isStrictPreflightEnabled(): Boolean =
        System.getProperty(PREFLIGHT_STRICT_PROPERTY, "false").toBoolean()

    private fun isFocusedSmokeEnabled(): Boolean =
        System.getProperty(FOCUSED_SMOKE_PROPERTY, "false").toBoolean()

    private fun logManifestSummary(snapshot: TACZAudioManifest) {
        val backendMode = resolveBackendMode()
        TACZLegacy.logger.info(
            MARKER,
            "Audio manifest ready: {} sounds (supported={}, incompatible={}, missing={}), backend={}",
            snapshot.totalCount,
            snapshot.supportedCount,
            snapshot.incompatibleCount,
            snapshot.missingCount,
            backendMode.name.lowercase(),
        )

        val shouldLogPreflight = isPreflightEnabled() || isFocusedSmokeEnabled()
        if (!shouldLogPreflight) {
            return
        }

        val incompatibleEntries = snapshot.entries.values.filter { !it.probeStatus.dedicatedCompatible }
        if (isFocusedSmokeEnabled()) {
            TACZLegacy.logger.info(
                "[FocusedSmoke][Audio] backend={} manifest={} supported={} incompatible={} missing={}",
                backendMode.name.lowercase(),
                snapshot.totalCount,
                snapshot.supportedCount,
                snapshot.incompatibleCount,
                snapshot.missingCount,
            )
        }

        for (descriptor in incompatibleEntries.take(MAX_PREFLIGHT_LOGGED_ISSUES)) {
            val referenceSummary = descriptor.references.joinToString(limit = 3, truncated = "…") { reference ->
                buildString {
                    append(reference.sourceType)
                    reference.ownerId?.let {
                        append('@')
                        append(it)
                    }
                    reference.key?.let {
                        append('#')
                        append(it)
                    }
                }
            }
            TACZLegacy.logger.warn(
                MARKER,
                "Audio preflight incompatible: sound={} asset={} status={} refs={} note={}",
                descriptor.soundId,
                descriptor.assetLocation ?: "<missing>",
                descriptor.probeStatus,
                referenceSummary.ifBlank { "<none>" },
                descriptor.notes ?: "n/a",
            )
            if (isFocusedSmokeEnabled()) {
                TACZLegacy.logger.info(
                    "[FocusedSmoke][Audio] incompatible sound={} asset={} status={} refs={} note={}",
                    descriptor.soundId,
                    descriptor.assetLocation ?: "<missing>",
                    descriptor.probeStatus,
                    referenceSummary.ifBlank { "<none>" },
                    descriptor.notes ?: "n/a",
                )
            }
        }

        if (incompatibleEntries.size > MAX_PREFLIGHT_LOGGED_ISSUES) {
            TACZLegacy.logger.warn(
                MARKER,
                "Audio preflight omitted {} additional incompatible sounds",
                incompatibleEntries.size - MAX_PREFLIGHT_LOGGED_ISSUES,
            )
        }

        if (isFocusedSmokeEnabled() && isStrictPreflightEnabled() && incompatibleEntries.isNotEmpty()) {
            TACZLegacy.logger.info(
                "[FocusedSmoke] FAIL reason=audio_preflight_incompatible count={}",
                incompatibleEntries.size,
            )
        }
    }

    private fun recordSubmission(record: TACZAudioSubmissionRecord) {
        synchronized(recentSubmissions) {
            if (recentSubmissions.size >= MAX_RECENT_SUBMISSIONS) {
                recentSubmissions.removeFirst()
            }
            recentSubmissions.addLast(record)
        }

        if (!isFocusedSmokeEnabled()) {
            return
        }

        val key = "${record.origin}|${record.soundId}|${record.backendMode}|${record.disposition}"
        if (focusedSmokeRequestKeys.add(key)) {
            TACZLegacy.logger.info(
                "[FocusedSmoke][Audio] request origin={} sound={} backend={} status={} result={} note={}",
                record.origin,
                record.soundId,
                record.backendMode.name.lowercase(),
                record.probeStatus,
                record.disposition,
                record.notes ?: "n/a",
            )
        }
    }
}
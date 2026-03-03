package com.tacz.legacy.client.sound

import com.jcraft.jogg.Packet
import com.jcraft.jogg.Page
import com.jcraft.jogg.StreamState
import com.jcraft.jogg.SyncState
import com.jcraft.jorbis.Block
import com.jcraft.jorbis.Comment
import com.jcraft.jorbis.DspState
import com.jcraft.jorbis.Info
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipFile

/**
 * 自研音频引擎：将所有 tacz 枪包音效在资源加载阶段一次性解码为 PCM，
 * 通过 OpenAL (LWJGL 2) 直接播放，完全绕过 Paulscode SoundSystem / CodecJOrbis。
 *
 * 设计动机：MC 1.12 的 Paulscode CommandThread 持有全局 THREAD_SYNC 锁；
 * 若 CodecJOrbis 解码挂起，后续所有 SoundSystem 调用（包括 tick 中的 playing() 查询）
 * 都会阻塞在锁上，导致客户端主线程冻结。
 *
 * 本引擎参考 TACZ 1.20.1 的 SoundAssetsManager / GunSoundInstance 架构：
 * 预加载 → ByteBuffer → OpenAL Buffer → Source 即时播放。
 */
public object TaczSoundEngine {

    private val logger = LogManager.getLogger("TaczSoundEngine")

    /** sound key (e.g. "tacz:ak47/inspect_1") → OpenAL buffer ID */
    private val soundBuffers = ConcurrentHashMap<String, Int>()

    /** 当前活跃的 OpenAL source ID 列表 */
    private val activeSources = CopyOnWriteArrayList<Int>()

    /** 预留给 MC 原生声音的 source 配额；剩余归 tacz 使用 */
    private const val MAX_CONCURRENT_SOURCES = 24

    /** JOrbis 解码缓冲区大小 */
    private const val OGG_READ_BUFFER_SIZE = 8192

    /** 单个音频文件解码上限（防止异常文件消耗过多内存），约 60 MB PCM */
    private const val MAX_DECODE_BYTES = 60 * 1024 * 1024

    /** 延迟预加载队列：在 OpenAL native 可用后再执行，避免 preInit 阶段崩溃 */
    private val deferredPreloadSources = CopyOnWriteArrayList<SoundPackSource>()

    @Volatile
    private var hasDeferredPreload: Boolean = false

    @Volatile
    private var openAlUnavailableLogged: Boolean = false

    // -------- public API --------

    /**
     * 从已挂载的枪包描述符中预加载所有 tacz_sounds 下的 ogg 音效。
     * 应在资源包安装后、主线程上调用（OpenAL context 绑定在主线程）。
     */
    public fun preloadFromPacks(packDescriptors: List<SoundPackSource>) {
        if (!isOpenAlAvailable()) {
            logger.warn("[TaczSoundEngine] OpenAL native not ready; skip immediate preload and wait for deferred tick preload")
            queuePreloadFromPacks(packDescriptors)
            return
        }

        val startTime = System.currentTimeMillis()
        var loadedCount = 0
        var failedCount = 0

        packDescriptors.forEach { source ->
            source.discoverSoundEntries().forEach entries@{ entry ->
                val soundKey = entry.soundKey
                if (soundBuffers.containsKey(soundKey)) {
                    return@entries
                }

                val pcmData = try {
                    entry.openStream().use { stream ->
                        decodeOggToPcm(stream)
                    }
                } catch (e: Exception) {
                    logger.warn("[TaczSoundEngine] Failed to decode: {} ({})", soundKey, e.message)
                    failedCount++
                    return@entries
                }

                if (pcmData == null) {
                    logger.debug("[TaczSoundEngine] Empty/invalid OGG: {}", soundKey)
                    failedCount++
                    return@entries
                }

                val bufferID = createOpenALBuffer(pcmData)
                if (bufferID != null) {
                    soundBuffers[soundKey] = bufferID
                    loadedCount++
                } else {
                    logger.warn("[TaczSoundEngine] OpenAL buffer creation failed: {}", soundKey)
                    failedCount++
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info(
            "[TaczSoundEngine] Preload complete: loaded={} failed={} total_keys={} elapsed={}ms",
            loadedCount, failedCount, soundBuffers.size, elapsed
        )

        hasDeferredPreload = false
        deferredPreloadSources.clear()
    }

    /**
     * 延迟预加载：可在 preInit 调用，不触发任何 OpenAL native 调用。
     * 真正预加载将在 tick 检测到 OpenAL 可用后执行。
     */
    public fun queuePreloadFromPacks(packDescriptors: List<SoundPackSource>) {
        deferredPreloadSources.clear()
        deferredPreloadSources.addAll(packDescriptors)
        hasDeferredPreload = packDescriptors.isNotEmpty()
    }

    public fun isSoundLoaded(soundKey: String): Boolean = soundBuffers.containsKey(soundKey)

    /**
     * 播放预加载的音效，直接通过 OpenAL 提交，不经过 Paulscode / CodecJOrbis。
     */
    public fun playSound(soundKey: String, x: Float, y: Float, z: Float, volume: Float, pitch: Float) {
        tryExecuteDeferredPreloadIfNeeded()
        if (!isOpenAlAvailable()) {
            return
        }

        val bufferID = soundBuffers[soundKey] ?: return

        cleanupFinishedSources()

        if (activeSources.size >= MAX_CONCURRENT_SOURCES) {
            val oldest = activeSources.removeAt(0)
            AL10.alSourceStop(oldest)
            AL10.alDeleteSources(oldest)
        }

        val sourceID = AL10.alGenSources()
        val error = AL10.alGetError()
        if (error != AL10.AL_NO_ERROR) {
            logger.debug("[TaczSoundEngine] alGenSources error: 0x{}", Integer.toHexString(error))
            return
        }

        AL10.alSourcei(sourceID, AL10.AL_BUFFER, bufferID)
        AL10.alSource3f(sourceID, AL10.AL_POSITION, x, y, z)
        AL10.alSourcef(sourceID, AL10.AL_GAIN, volume.coerceIn(0f, 1f))
        AL10.alSourcef(sourceID, AL10.AL_PITCH, pitch.coerceIn(0.5f, 2f))
        AL10.alSourcei(sourceID, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE)
        AL10.alSourcef(sourceID, AL10.AL_ROLLOFF_FACTOR, 1f)
        AL10.alSourcef(sourceID, AL10.AL_REFERENCE_DISTANCE, 2f)
        AL10.alSourcef(sourceID, AL10.AL_MAX_DISTANCE, 16f)
        AL10.alSourcePlay(sourceID)

        activeSources.add(sourceID)
    }

    /** 每帧调用，清理已播放完毕的 source */
    public fun tick() {
        tryExecuteDeferredPreloadIfNeeded()
        if (!isOpenAlAvailable()) {
            return
        }

        cleanupFinishedSources()
    }

    /** 释放所有 OpenAL 资源（mod 卸载 / 资源重载前调用） */
    public fun cleanup() {
        if (isOpenAlAvailable()) {
            activeSources.forEach { sourceID ->
                AL10.alSourceStop(sourceID)
                AL10.alDeleteSources(sourceID)
            }

            soundBuffers.values.forEach { bufferID ->
                AL10.alDeleteBuffers(bufferID)
            }
        }

        activeSources.clear()
        deferredPreloadSources.clear()
        hasDeferredPreload = false

        soundBuffers.clear()
    }

    public fun loadedSoundCount(): Int = soundBuffers.size

    // -------- internal --------

    private fun tryExecuteDeferredPreloadIfNeeded() {
        if (!hasDeferredPreload) {
            return
        }
        if (!isOpenAlAvailable()) {
            return
        }

        val sources = deferredPreloadSources.toList()
        if (sources.isEmpty()) {
            hasDeferredPreload = false
            return
        }

        logger.info("[TaczSoundEngine] Executing deferred preload, sources={}", sources.size)
        preloadFromPacks(sources)
    }

    private fun isOpenAlAvailable(): Boolean {
        return try {
            AL10.alGetError()
            openAlUnavailableLogged = false
            true
        } catch (_: UnsatisfiedLinkError) {
            if (!openAlUnavailableLogged) {
                openAlUnavailableLogged = true
                logger.debug("[TaczSoundEngine] OpenAL native not available yet; waiting for engine init")
            }
            false
        } catch (_: NoClassDefFoundError) {
            if (!openAlUnavailableLogged) {
                openAlUnavailableLogged = true
                logger.debug("[TaczSoundEngine] OpenAL classes not ready yet; waiting for engine init")
            }
            false
        }
    }

    private fun cleanupFinishedSources() {
        if (!isOpenAlAvailable()) {
            return
        }

        val toRemove = mutableListOf<Int>()
        activeSources.forEach { sourceID ->
            val state = AL10.alGetSourcei(sourceID, AL10.AL_SOURCE_STATE)
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
                AL10.alDeleteSources(sourceID)
                toRemove.add(sourceID)
            }
        }
        if (toRemove.isNotEmpty()) {
            activeSources.removeAll(toRemove.toSet())
        }
    }

    private fun createOpenALBuffer(pcm: PcmData): Int? {
        if (!isOpenAlAvailable()) {
            return null
        }

        val format = when (pcm.channels) {
            1 -> if (pcm.bitsPerSample == 8) AL10.AL_FORMAT_MONO8 else AL10.AL_FORMAT_MONO16
            2 -> if (pcm.bitsPerSample == 8) AL10.AL_FORMAT_STEREO8 else AL10.AL_FORMAT_STEREO16
            else -> {
                logger.debug("[TaczSoundEngine] Unsupported channel count: {}", pcm.channels)
                return null
            }
        }

        val byteBuffer: ByteBuffer = BufferUtils.createByteBuffer(pcm.data.size)
        byteBuffer.put(pcm.data)
        byteBuffer.flip()

        AL10.alGetError() // clear
        val bufferID = AL10.alGenBuffers()
        AL10.alBufferData(bufferID, format, byteBuffer, pcm.sampleRate)

        val error = AL10.alGetError()
        if (error != AL10.AL_NO_ERROR) {
            logger.debug("[TaczSoundEngine] alBufferData error: 0x{}", Integer.toHexString(error))
            AL10.alDeleteBuffers(bufferID)
            return null
        }
        return bufferID
    }

    // -------- OGG / Vorbis decoder using JOrbis --------

    internal fun decodeOggToPcm(inputStream: InputStream): PcmData? {
        val syncState = SyncState()
        val streamState = StreamState()
        val page = Page()
        val packet = Packet()
        val info = Info()
        val comment = Comment()
        val dspState = DspState()
        val block = Block(dspState)

        val pcmOutput = ByteArrayOutputStream()

        try {
            syncState.init()
            info.init()
            comment.init()

            var headersParsed = false
            var packetCount = 0
            var eos = false

            outer@ while (!eos) {
                // Feed data into sync state
                val bufferIndex = syncState.buffer(OGG_READ_BUFFER_SIZE)
                val buffer = syncState.data
                val bytesRead = inputStream.read(buffer, bufferIndex, OGG_READ_BUFFER_SIZE)
                if (bytesRead <= 0) {
                    syncState.wrote(0)
                    break
                }
                syncState.wrote(bytesRead)

                // Extract pages
                while (true) {
                    val pageResult = syncState.pageout(page)
                    if (pageResult == 0) break // need more data
                    if (pageResult < 0) continue // corrupt, skip

                    if (!headersParsed && packetCount == 0) {
                        streamState.init(page.serialno())
                    }

                    streamState.pagein(page)

                    // Extract packets from page
                    while (true) {
                        val packetResult = streamState.packetout(packet)
                        if (packetResult == 0) break // need more pages
                        if (packetResult < 0) continue // skip corrupt

                        if (packetCount < 3) {
                            // Header packets (identification, comment, setup)
                            if (info.synthesis_headerin(comment, packet) < 0) {
                                logger.debug("[TaczSoundEngine] Invalid Vorbis header at packet {}", packetCount)
                                return null
                            }
                            packetCount++
                            if (packetCount == 3) {
                                headersParsed = true
                                dspState.synthesis_init(info)
                                block.init(dspState)
                            }
                            continue
                        }

                        // Audio data packet
                        if (block.synthesis(packet) == 0) {
                            dspState.synthesis_blockin(block)
                        }

                        val pcmArray = Array<Array<FloatArray>>(1) { emptyArray() }
                        val index = IntArray(info.channels)

                        while (true) {
                            val samples = dspState.synthesis_pcmout(pcmArray, index)
                            if (samples <= 0) break

                            val channelData = pcmArray[0]
                            for (i in 0 until samples) {
                                for (ch in 0 until info.channels) {
                                    var sample = (channelData[ch][index[ch] + i] * 32767f).toInt()
                                    sample = sample.coerceIn(-32768, 32767)
                                    // Little-endian 16-bit PCM
                                    pcmOutput.write(sample and 0xFF)
                                    pcmOutput.write((sample shr 8) and 0xFF)
                                }
                            }
                            dspState.synthesis_read(samples)

                            if (pcmOutput.size() > MAX_DECODE_BYTES) {
                                logger.warn("[TaczSoundEngine] Decode size limit exceeded, truncating")
                                break@outer
                            }
                        }
                    }

                    if (page.eos() != 0) {
                        eos = true
                    }
                }
            }

            if (!headersParsed) {
                return null
            }

            val pcmBytes = pcmOutput.toByteArray()
            if (pcmBytes.isEmpty()) {
                return null
            }

            return PcmData(
                data = pcmBytes,
                channels = info.channels,
                sampleRate = info.rate,
                bitsPerSample = 16
            )
        } catch (e: Exception) {
            logger.debug("[TaczSoundEngine] OGG decode exception: {}", e.message)
            return null
        } finally {
            runCatching { block.clear() }
            runCatching { dspState.clear() }
            runCatching { info.clear() }
            // comment.clear() is package-private in JOrbis, skip it
            runCatching { streamState.clear() }
            runCatching { syncState.clear() }
        }
    }

    // -------- data types --------

    internal data class PcmData(
        val data: ByteArray,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int
    )

    /** 音效包数据源的抽象，由 GunPackExternalResourcePackManager 构造 */
    public interface SoundPackSource {
        public fun discoverSoundEntries(): List<SoundEntry>
    }

    public interface SoundEntry {
        public val soundKey: String
        public fun openStream(): InputStream
    }

    // -------- directory / zip pack source implementations --------

    public class DirectorySoundPackSource(
        private val namespace: String,
        private val packRoot: Path,
        private val customPackId: String? = null
    ) : SoundPackSource {

        override fun discoverSoundEntries(): List<SoundEntry> {
            val entries = mutableListOf<SoundEntry>()
            val searchRoots = mutableListOf<Path>()

            // Standard: assets/<namespace>/tacz_sounds/
            val taczSoundsDir = packRoot.resolve("assets").resolve(namespace).resolve("tacz_sounds")
            if (Files.isDirectory(taczSoundsDir)) searchRoots.add(taczSoundsDir)

            // Standard: assets/<namespace>/sounds/
            val vanillaSoundsDir = packRoot.resolve("assets").resolve(namespace).resolve("sounds")
            if (Files.isDirectory(vanillaSoundsDir)) searchRoots.add(vanillaSoundsDir)

            // Custom nested: assets/<namespace>/custom/<packId>/assets/<namespace>/tacz_sounds/
            if (customPackId != null) {
                val customTacz = packRoot.resolve("assets").resolve(namespace)
                    .resolve("custom").resolve(customPackId)
                    .resolve("assets").resolve(namespace).resolve("tacz_sounds")
                if (Files.isDirectory(customTacz)) searchRoots.add(customTacz)

                val customVanilla = packRoot.resolve("assets").resolve(namespace)
                    .resolve("custom").resolve(customPackId)
                    .resolve("assets").resolve(namespace).resolve("sounds")
                if (Files.isDirectory(customVanilla)) searchRoots.add(customVanilla)
            }

            searchRoots.forEach { root ->
                runCatching {
                    Files.walk(root).use { stream ->
                        stream.forEach { file ->
                            if (!Files.isRegularFile(file)) return@forEach
                            val fileName = file.fileName.toString()
                            if (!fileName.endsWith(".ogg", ignoreCase = true)) return@forEach

                            val relative = root.relativize(file).toString()
                                .replace('\\', '/')
                                .trimStart('/')
                            val key = "$namespace:${relative.substringBeforeLast('.')}"

                            entries.add(object : SoundEntry {
                                override val soundKey: String = key
                                override fun openStream(): InputStream = Files.newInputStream(file)
                            })
                        }
                    }
                }
            }

            return entries
        }
    }

    public class ZipSoundPackSource(
        private val namespace: String,
        private val zipPath: Path,
        private val zipPrefixes: List<String>,
        private val customPackId: String? = null
    ) : SoundPackSource {

        override fun discoverSoundEntries(): List<SoundEntry> {
            val entries = mutableListOf<SoundEntry>()
            val seenKeys = mutableSetOf<String>()

            runCatching {
                ZipFile(zipPath.toFile()).use { zip ->
                    val enumeration = zip.entries()
                    while (enumeration.hasMoreElements()) {
                        val entry = enumeration.nextElement()
                        if (entry.isDirectory) continue
                        if (!entry.name.endsWith(".ogg", ignoreCase = true)) continue

                        val normalizedName = entry.name.replace('\\', '/').trimStart('/')

                        for (prefix in zipPrefixes) {
                            val normalizedPrefix = if (prefix.isBlank()) "" else
                                prefix.replace('\\', '/').trimEnd('/') + "/"

                            val soundKey = tryExtractSoundKey(normalizedName, normalizedPrefix, namespace, customPackId)
                            if (soundKey != null && seenKeys.add(soundKey)) {
                                val entryName = entry.name
                                entries.add(object : SoundEntry {
                                    override val soundKey: String = soundKey
                                    override fun openStream(): InputStream {
                                        val zf = ZipFile(zipPath.toFile())
                                        val ze = zf.getEntry(entryName)
                                            ?: throw java.io.FileNotFoundException("ZIP entry not found: $entryName")
                                        val rawStream = zf.getInputStream(ze)
                                        return object : java.io.FilterInputStream(rawStream) {
                                            override fun close() {
                                                super.close()
                                                runCatching { zf.close() }
                                            }
                                        }
                                    }
                                })
                                break // first matching prefix wins
                            }
                        }
                    }
                }
            }

            return entries
        }

        public companion object {
            internal fun tryExtractSoundKey(
                normalizedEntryName: String,
                normalizedPrefix: String,
                namespace: String,
                customPackId: String?
            ): String? {
                val prefixedPaths = mutableListOf<String>()
                prefixedPaths.add("${normalizedPrefix}assets/$namespace/tacz_sounds/")
                prefixedPaths.add("${normalizedPrefix}assets/$namespace/sounds/")
                if (customPackId != null) {
                    prefixedPaths.add("${normalizedPrefix}assets/$namespace/custom/$customPackId/assets/$namespace/tacz_sounds/")
                    prefixedPaths.add("${normalizedPrefix}assets/$namespace/custom/$customPackId/assets/$namespace/sounds/")
                }

                for (pathPrefix in prefixedPaths) {
                    if (normalizedEntryName.startsWith(pathPrefix, ignoreCase = true)) {
                        val relative = normalizedEntryName.removePrefix(pathPrefix).trimStart('/')
                        if (relative.isBlank()) continue
                        val keyPath = relative.substringBeforeLast('.')
                        if (keyPath.isBlank()) continue
                        return "$namespace:$keyPath"
                    }
                }
                return null
            }
        }
    }
}

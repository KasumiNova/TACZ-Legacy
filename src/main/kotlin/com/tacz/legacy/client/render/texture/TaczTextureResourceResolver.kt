package com.tacz.legacy.client.render.texture

import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation

public object TaczTextureResourceResolver {

    public fun resolveForBind(
        rawPath: String?,
        sourceId: String? = null,
        fallback: ResourceLocation? = null,
        minecraft: Minecraft = Minecraft.getMinecraft()
    ): ResourceLocation? {
        val candidates = candidateResources(rawPath, sourceId)
        candidates.firstOrNull { resource -> hasResource(minecraft, resource) }?.let { found ->
            return found
        }

        if (fallback != null && hasResource(minecraft, fallback)) {
            return fallback
        }

        return null
    }

    public fun resolveExisting(
        rawPath: String?,
        sourceId: String? = null,
        minecraft: Minecraft = Minecraft.getMinecraft()
    ): ResourceLocation? {
        val candidates = candidateResources(rawPath, sourceId)
        return candidates.firstOrNull { resource -> hasResource(minecraft, resource) }
    }

    public fun candidateResources(rawPath: String?, sourceId: String? = null): List<ResourceLocation> {
        val normalized = rawPath
            ?.trim()
            ?.removePrefix("assets/")
            ?.ifBlank { null }
            ?: return emptyList()

        val slash = normalized.indexOf('/')
        if (slash <= 0 || slash >= normalized.length - 1) {
            return emptyList()
        }

        val namespace = normalized.substring(0, slash)
        val path = normalized.substring(slash + 1)

        val out = linkedSetOf<ResourceLocation>()
        addCandidate(out, namespace, path)

        val packCandidates = sourceId
            ?.let(::extractPackIdCandidates)
            .orEmpty()
        packCandidates.forEach { packId ->
            addCandidate(out, namespace, "custom/$packId/assets/$namespace/$path")
        }

        // 兼容内嵌默认枪包资源：assets/tacz/custom/tacz_default_gun/assets/tacz/... 
        addCandidate(out, namespace, "custom/tacz_default_gun/assets/$namespace/$path")

        return out.toList()
    }

    private fun addCandidate(
        out: MutableSet<ResourceLocation>,
        namespace: String,
        path: String
    ) {
        runCatching { ResourceLocation(namespace, path) }
            .getOrNull()
            ?.let(out::add)
    }

    private fun hasResource(minecraft: Minecraft, resource: ResourceLocation): Boolean {
        return runCatching {
            minecraft.resourceManager.getResource(resource).use { _ -> true }
        }.getOrElse { false }
    }

    private fun extractPackIdCandidates(sourceId: String): List<String> {
        val trimmed = sourceId.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        val packToken = trimmed
            .substringBefore("!/")
            .substringBefore('/')
            .trim()

        if (packToken.isEmpty()) {
            return emptyList()
        }

        return linkedSetOf<String>().apply {
            add(packToken)
            if (packToken.endsWith(".zip", ignoreCase = true)) {
                add(packToken.removeSuffix(".zip"))
            }
            normalizePackId(packToken)?.let(::add)
            if (packToken.endsWith(".zip", ignoreCase = true)) {
                normalizePackId(packToken.removeSuffix(".zip"))?.let(::add)
            }
        }.toList()
    }

    private fun normalizePackId(raw: String): String? {
        val normalized = raw.trim()
            .lowercase()
            .map { ch -> if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')
        return normalized.ifBlank { null }
    }

}
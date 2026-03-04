package com.tacz.legacy.common.application.tooltip

public data class LegacyRichTooltipDocument(
    val lines: List<LegacyRichTooltipLine>
) {
    public val requiresCustomRender: Boolean
        get() = lines.any { line -> line.segments.any { it is LegacyRichTooltipSegment.Icon } }

    public companion object {
        public val EMPTY: LegacyRichTooltipDocument = LegacyRichTooltipDocument(emptyList())
    }
}

public data class LegacyRichTooltipLine(
    val segments: List<LegacyRichTooltipSegment>
) {
    public val isEmpty: Boolean
        get() = segments.isEmpty() || segments.all { segment ->
            segment is LegacyRichTooltipSegment.Text && segment.text.isBlank()
        }
}

public sealed class LegacyRichTooltipSegment {
    public data class Text(
        val text: String
    ) : LegacyRichTooltipSegment()

    public data class Icon(
        val namespace: String,
        val texturePath: String,
        val size: Int
    ) : LegacyRichTooltipSegment() {
        public val resourcePath: String
            get() = "$namespace:$texturePath"
    }
}

public object LegacyRichTooltipParser {

    public fun parseLines(lines: List<String>): LegacyRichTooltipDocument {
        if (lines.isEmpty()) {
            return LegacyRichTooltipDocument.EMPTY
        }

        val parsedLines = lines.map(::parseLine)
        return LegacyRichTooltipDocument(parsedLines)
    }

    private fun parseLine(line: String): LegacyRichTooltipLine {
        if (line.isEmpty()) {
            return LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("")))
        }

        val segments = mutableListOf<LegacyRichTooltipSegment>()
        var cursor = 0

        ICON_TOKEN_REGEX.findAll(line).forEach { match ->
            val start = match.range.first
            val endExclusive = match.range.last + 1

            if (start > cursor) {
                segments += LegacyRichTooltipSegment.Text(line.substring(cursor, start))
            }

            val rawResource = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val rawSize = match.groupValues.getOrNull(2)?.trim().orEmpty()
            val icon = parseIconToken(rawResource, rawSize)
            if (icon != null) {
                segments += icon
            } else {
                segments += LegacyRichTooltipSegment.Text(match.value)
            }

            cursor = endExclusive
        }

        if (cursor < line.length) {
            segments += LegacyRichTooltipSegment.Text(line.substring(cursor))
        }

        if (segments.isEmpty()) {
            segments += LegacyRichTooltipSegment.Text(line)
        }

        return LegacyRichTooltipLine(segments)
    }

    private fun parseIconToken(rawResource: String, rawSize: String): LegacyRichTooltipSegment.Icon? {
        if (rawResource.isBlank()) {
            return null
        }

        val namespace = rawResource.substringBefore(':').trim().lowercase().ifBlank { return null }
        val rawPath = rawResource.substringAfter(':', missingDelimiterValue = "").trim().ifBlank { return null }

        var normalizedPath = rawPath
            .replace('\\', '/')
            .trim('/')
            .lowercase()

        if (!normalizedPath.startsWith("textures/")) {
            normalizedPath = "textures/$normalizedPath"
        }
        if (!normalizedPath.endsWith(".png")) {
            normalizedPath = "$normalizedPath.png"
        }

        val size = rawSize.toIntOrNull()
            ?.coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE)
            ?: DEFAULT_ICON_SIZE

        return LegacyRichTooltipSegment.Icon(
            namespace = namespace,
            texturePath = normalizedPath,
            size = size
        )
    }

    private val ICON_TOKEN_REGEX: Regex = Regex("\\[icon:([a-zA-Z0-9_.-]+:[^,\\]]+)(?:,([0-9]{1,3}))?]")

    private const val DEFAULT_ICON_SIZE: Int = 10
    private const val MIN_ICON_SIZE: Int = 6
    private const val MAX_ICON_SIZE: Int = 32
}

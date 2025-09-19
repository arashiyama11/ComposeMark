package io.github.arashiyama11.composemark.plugin.frontmatter

internal data class FrontMatterParseResult(
    val section: ConfigSection,
    val body: String,
    val consumedText: String,
)

internal data class ConfigSection(
    val rawText: String,
    val formatHint: String?,
    val contentStartLine: Int,
)

internal fun parseFrontMatter(source: String): FrontMatterParseResult? {
    if (source.isEmpty()) return null

    var cursor = 0
    if (source.startsWith("\uFEFF")) {
        cursor = 1
    }

    if (!source.startsWith("---", cursor)) return null
    val indicatorEnd = cursor + 3
    if (indicatorEnd < source.length && !source[indicatorEnd].isLineTerminator()) {
        return null
    }

    val firstLineBreak = source.indexOfLineBreak(indicatorEnd)
    val sectionStart = if (firstLineBreak == -1) source.length else firstLineBreak + 1
    var line = 2
    var scanIndex = sectionStart

    while (scanIndex <= source.length) {
        val nextBreak = source.indexOfLineBreak(scanIndex)
        val lineEnd = if (nextBreak == -1) source.length else nextBreak
        val lineContent = source.substring(scanIndex, lineEnd).trimEnd('\r')
        if (lineContent == "---") {
            val sectionEnd = scanIndex
            val consumedEnd = if (nextBreak == -1) lineEnd else nextBreak + 1
            val raw = source.substring(sectionStart, sectionEnd)
            val consumed = source.substring(0, consumedEnd)
            val body = source.substring(consumedEnd)
            val formatHint = guessFormat(raw)
            return FrontMatterParseResult(
                section = ConfigSection(
                    rawText = raw,
                    formatHint = formatHint,
                    contentStartLine = line + 1,
                ),
                body = body,
                consumedText = consumed,
            )
        }
        if (nextBreak == -1) break
        scanIndex = nextBreak + 1
        line += 1
    }

    return null
}

private fun guessFormat(raw: String): String? {
    val trimmed = raw.trimStart()
    if (trimmed.isEmpty()) return null
    val lowered = trimmed.lowercase()
    return when {
        trimmed.startsWith("{") -> "json"
        trimmed.startsWith("[") -> "json"
        lowered.contains("=") -> "toml"
        else -> null
    }
}

private fun String.indexOfLineBreak(start: Int): Int {
    var i = start
    val last = length
    while (i < last) {
        val ch = this[i]
        if (ch == '\n') return i
        if (ch == '\r') {
            return i
        }
        i += 1
    }
    return -1
}

private fun Char.isLineTerminator(): Boolean = this == '\n' || this == '\r'

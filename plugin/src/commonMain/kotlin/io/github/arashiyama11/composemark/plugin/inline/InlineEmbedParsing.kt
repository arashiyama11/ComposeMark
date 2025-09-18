package io.github.arashiyama11.composemark.plugin.inline

import androidx.compose.runtime.Immutable
import io.github.arashiyama11.composemark.core.Block
import io.github.arashiyama11.composemark.core.BlockItem

@Immutable
internal data class CmSection(
    val markdown: String? = null,
    val attrs: Map<String, String?>? = null,
)

internal fun parseComposableSections(
    src: String,
    startPrefix: String = "<Composable",
    endTag: String = "</Composable>",
    attrsParser: (String) -> Map<String, String?> = ::parseComposableAttrs,
): List<CmSection> {
    val list = mutableListOf<CmSection>()
    var cursor = 0
    while (cursor < src.length) {
        val start = src.indexOf(startPrefix, cursor)
        if (start == -1) {
            if (cursor < src.length) list += CmSection(markdown = src.substring(cursor))
            break
        }
        if (start > cursor) list += CmSection(markdown = src.substring(cursor, start))

        val close = src.indexOf('>', start)
        if (close == -1) {
            list += CmSection(markdown = src.substring(start))
            break
        }
        val open = src.substring(start, close + 1)
        val attrsText = open.removePrefix(startPrefix).removeSuffix(">")
        val attrs = attrsParser(attrsText)
        val innerStart = close + 1
        val end = src.indexOf(endTag, innerStart)
        if (end == -1) {
            list += CmSection(markdown = src.substring(innerStart))
            break
        }
        list += CmSection(markdown = null, attrs = attrs)
        cursor = end + endTag.length
    }
    return list
}

internal fun parseComposableAttrs(
    raw: String,
    allowBareFlagTrue: Boolean = true,
    allowedKeyFirstChars: Set<Char> = (('a'..'z') + ('A'..'Z') + setOf('_')).toSet(),
    allowedKeyChars: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + setOf('_', '-')).toSet(),
): Map<String, String?> {
    val text = raw.trim()
    if (text.isEmpty()) return emptyMap()
    val attrs = linkedMapOf<String, String?>()
    var i = 0

    fun skipWs() {
        while (i < text.length && text[i].isWhitespace()) i++
    }

    fun readIdent(): String {
        val start = i
        if (i >= text.length) return ""
        if (text[i] !in allowedKeyFirstChars) return ""
        i++
        while (i < text.length && text[i] in allowedKeyChars) i++
        return text.substring(start, i)
    }

    fun readValue(): String {
        if (i >= text.length) return ""
        return when (text[i]) {
            '"' -> {
                i++
                val s = i
                while (i < text.length && text[i] != '"') i++
                val v = text.substring(s, i)
                if (i < text.length) i++
                v
            }

            '\'' -> {
                i++
                val s = i
                while (i < text.length && text[i] != '\'') i++
                val v = text.substring(s, i)
                if (i < text.length) i++
                v
            }

            else -> {
                val s = i
                while (i < text.length && !text[i].isWhitespace() && text[i] != '>') i++
                text.substring(s, i)
            }
        }
    }

    while (i < text.length) {
        skipWs()
        val key = readIdent()
        if (key.isEmpty()) break
        skipWs()
        val value = if (i < text.length && text[i] == '=') {
            i++; skipWs(); readValue()
        } else if (allowBareFlagTrue) {
            "true"
        } else null
        attrs[key] = value
    }
    return attrs
}

internal fun mergeMarkdownBlock(target: BlockItem, appendSource: String, path: String?): BlockItem {
    return Block.markdown(
        source = target.source + appendSource,
        path = path,
    )
}

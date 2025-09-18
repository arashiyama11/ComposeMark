package io.github.arashiyama11.composemark.sample

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.PreProcessorMetadataKey
import io.github.arashiyama11.composemark.core.createComposeMarkPlugin


class HeaderConfigConfig {
    var headerModifier: Modifier = Modifier
    var headerContent: (@Composable (title: String, Modifier) -> Unit)? = null

    fun headerContent(content: @Composable (title: String, Modifier) -> Unit) {
        headerContent = content
    }
}

val HeaderConfigPlugin = createComposeMarkPlugin { buildConfig ->
    val config = HeaderConfigConfig().apply(buildConfig)

    onMarkdownBlockPreProcess {
        val headerSection =
            it.data.source.lineSequence().takeWhile { it != "---" }.joinToString("\n")
        if (headerSection.trim().isBlank() || headerSection == it.data.source) {
            proceed()
            return@onMarkdownBlockPreProcess
        }
        val config = parseMarpFrontMatter(headerSection)
        if (config.containsKey("title")) {
            it.metadata[TitleKey] = config["title"]!!
        }
        if (config.containsKey("theme")) {
            it.metadata[ThemeKey] = config["theme"]!!
        }

        val md = it.data.source.lineSequence().dropWhile { it != "---" }.drop(1)
            .joinToString("\n")
        proceedWith(it.copy(data = it.data.copy(source = md), metadata = it.metadata))
    }

    onRenderMarkdownBlock {
        Log.d("Debug", "Title: ${it.metadata[TitleKey]}, Theme: ${it.metadata[ThemeKey]}")
        var subject = it.metadata[TitleKey]?.let { title ->
            it.copy(metadata = it.metadata) { modifier ->
                if (config.headerContent != null) {
                    config.headerContent!!.invoke(title, modifier)
                } else {
                    Box(
                        modifier = config.headerModifier
                            .background(MaterialTheme.colorScheme.primary)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                        )
                    }
                }
                it.content(Modifier)
            }
        } ?: it

        proceedWith(subject)
    }
}
val TitleKey = PreProcessorMetadataKey<String>("title")
val ThemeKey = PreProcessorMetadataKey<String>("theme")

fun parseMarpFrontMatter(input: String): Map<String, String> {
    fun stripInlineCommentOutsideQuotes(s: String): String {
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { // エスケープは次文字をそのまま取り込む
                sb.append(c)
                sb.append(s[i + 1])
                i += 2
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle
                sb.append(c)
                i++
                continue
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble
                sb.append(c)
                i++
                continue
            }
            if (c == '#' && !inSingle && !inDouble) {
                // クォートの外側に来たコメント開始 -> 切る
                break
            }
            sb.append(c)
            i++
        }
        return sb.toString().trim()
    }

    fun unquoteIfNeeded(s: String): String {
        if (s.length >= 2) {
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                return s.substring(1, s.length - 1)
            }
        }
        return s
    }

    val result = linkedMapOf<String, String>()
    input.lineSequence().forEach { rawLine ->
        var line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        if (line.startsWith("---")) return@forEach
        if (line.startsWith("#")) return@forEach

        val colonIndex = line.indexOf(':')
        if (colonIndex == -1) return@forEach

        val key = line.substring(0, colonIndex).trim()
        var valuePart = line.substring(colonIndex + 1).trim()
        valuePart = stripInlineCommentOutsideQuotes(valuePart)
        valuePart = unquoteIfNeeded(valuePart)
        result[key] = valuePart
    }
    return result
}
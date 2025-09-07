package io.github.arashiyama11.composemark.plugin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arashiyama11.composemark.core.ComposablePipelineContent
import io.github.arashiyama11.composemark.core.ComposeMarkPlugin
import io.github.arashiyama11.composemark.core.PreProcessorMetadataKey
import io.github.arashiyama11.composemark.core.composeMarkPlugin
import kotlinx.coroutines.launch

/**
 * Basic page scaffold that wraps rendered blocks and exposes
 * heading and breadcrumb metadata for ScrollSpy/TOC.
 */
public class PageScaffoldConfig {
    public var enableToc: Boolean = true
    public var showBreadcrumbs: Boolean = true

    public var tocNumbering: Boolean = false
    public var headingItem: (@Composable (HeadingInfo) -> Unit)? = null
    public var scaffold: (@Composable (PageScaffoldProps, Modifier) -> Unit)? = null
    public var injectHeadingIds: Boolean = false

    public fun headingItem(item: @Composable (HeadingInfo) -> Unit) {
        headingItem = item
    }

    public fun scaffold(scaffold: @Composable (PageScaffoldProps, Modifier) -> Unit) {
        this.scaffold = scaffold
    }
}

public data class HeadingInfo(
    val level: Int,
    val line: String,
    val text: String,
    val anchor: String,
    val blockIndex: Int? = null,
)

public data class Breadcrumb(
    val label: String,
    val fullPath: String,
)

public data class PageScaffoldProps(
    val headings: List<HeadingInfo>,
    val breadcrumbs: List<Breadcrumb>,
    val content: @Composable (Modifier) -> Unit,
    val jumpTo: suspend (HeadingInfo) -> Unit,
)

public val PageHeadingsKey: PreProcessorMetadataKey<List<HeadingInfo>> =
    PreProcessorMetadataKey("page.headings")
public val PagePathKey: PreProcessorMetadataKey<String> = PreProcessorMetadataKey("page.path")
public val BreadcrumbsKey: PreProcessorMetadataKey<List<Breadcrumb>> =
    PreProcessorMetadataKey("page.breadcrumbs")

public enum class PageScaffoldApplied { Default, Custom }

public val PageScaffoldAppliedKey: PreProcessorMetadataKey<PageScaffoldApplied> =
    PreProcessorMetadataKey("page.scaffold.applied")


public val LocalAnchorModifier: ProvidableCompositionLocal<@Composable ((String) -> Modifier)> =
    staticCompositionLocalOf { error("No LocalAnchorModifier provided") }

public val LocalBlockHeightModifier: ProvidableCompositionLocal<@Composable ((Int) -> Modifier)> =
    staticCompositionLocalOf { error("No LocalBlockHeightModifier provided") }

internal fun parseHeadingsFromMarkdownSource(source: String): List<HeadingInfo> {
    // Very small, dependency-free heading parser for ATX style (# .. ######)
    return source.lineSequence().mapNotNull { line ->
        val match = "^(#{1,6})\\s+(.+)$".toRegex().find(line.trim()) ?: return@mapNotNull null
        val level = match.groupValues[1].length
        val raw = match.groupValues[2].trim().trimEnd('#').trim()
        val anchor = slugify(raw)
        HeadingInfo(level, line, raw, anchor)
    }.toList()
}

internal fun computeHeadingPositions(source: String, parsed: List<HeadingInfo>): Map<String, Int> {
    if (parsed.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, Int>()
    var offset = 0
    val lines = source.lines()
    var idxParsed = 0
    val lineRegex = "^(#{1,6})\\s+(.+)$".toRegex()
    for (line in lines) {
        val m = lineRegex.find(line.trim())
        if (m != null && idxParsed < parsed.size) {
            val h = parsed[idxParsed]
            result[h.anchor] = offset
            idxParsed++
        }
        offset += line.length + 1
        if (idxParsed >= parsed.size) break
    }
    return result
}

internal fun computeHeadingRatios(source: String, parsed: List<HeadingInfo>): Map<String, Float> {
    val total = source.length.coerceAtLeast(1)
    return computeHeadingPositions(source, parsed).mapValues { (_, pos) -> pos.toFloat() / total }
}

internal fun computeBreadcrumbsFromPath(path: String): List<Breadcrumb> {
    val clean = path.trim().trim('/')
    if (clean.isEmpty()) return emptyList()
    val parts = clean.split('/')
    var acc = ""
    return parts.mapIndexed { idx, seg ->
        val isFile = idx == parts.lastIndex
        val label = if (isFile) seg.substringBeforeLast('.') else seg
        acc = if (acc.isEmpty()) seg else "$acc/$seg"
        Breadcrumb(label = label, fullPath = acc)
    }
}

public val PageScaffoldPlugin: ComposeMarkPlugin<PageScaffoldConfig> =
    composeMarkPlugin(::PageScaffoldConfig) { config ->
        if (config.injectHeadingIds) {
            onMarkdownBlockPreProcess { subject ->
                val seen = mutableMapOf<String, Int>()
                fun uniqueSlug(base: String): String {
                    val c = (seen[base] ?: 0) + 1
                    seen[base] = c
                    return if (c == 1) base else "$base-$c"
                }

                val headingRegex = Regex(
                    pattern = "^(#{1,6})\\s+(.+?)\\s*(\\{#([a-z0-9\\-]+)\\})?\\s*$",
                    option = RegexOption.IGNORE_CASE
                )
                val transformed = subject.content.source.lineSequence().map { line ->
                    val m = headingRegex.find(line)
                    if (m == null) return@map line
                    val hashes = m.groupValues[1]
                    val text = m.groupValues[2].trim().trimEnd('#').trim()
                    val explicit = m.groupValues.getOrNull(4).orEmpty()
                    if (explicit.isNotBlank()) {
                        val used = explicit.lowercase()
                        seen[used] = (seen[used] ?: 0) + 1
                        return@map "$hashes $text {#$used}"
                    }
                    val base = slugify(text)
                    val unique = uniqueSlug(base)
                    "$hashes $text {#$unique}"
                }.joinToString("\n")

                proceedWith(subject.copy(content = subject.content.copy(source = transformed)))
            }
        }

        // Capture path into metadata so render phase can compute breadcrumbs.
        onBlockListPreProcess { subject ->
            val headings = subject.content.blocks.flatMapIndexed { i, block ->
                parseHeadingsFromMarkdownSource(block.source).map {
                    if (it.blockIndex == null) it.copy(blockIndex = i) else it
                }
            }

            subject.metadata[PageHeadingsKey] = headings
            proceed()
        }

        onRenderBlocks {

            val headings =
                it.metadata[PageHeadingsKey] ?: parseHeadingsFromMarkdownSource(it.source)

            if (headings.isNotEmpty()) {
                it.metadata[PageHeadingsKey] = headings
            }

            val storedPath = it.metadata[PagePathKey]
            if (storedPath != null && config.showBreadcrumbs) {
                it.metadata[BreadcrumbsKey] = computeBreadcrumbsFromPath(storedPath)
            }

            val custom = config.scaffold
            it.metadata[PageScaffoldAppliedKey] =
                if (custom != null) PageScaffoldApplied.Custom else PageScaffoldApplied.Default

            val wrapped: ComposablePipelineContent = it.copy { mod: Modifier ->
                val scroll = rememberScrollState()
                val scope = rememberCoroutineScope()
                val blockHeights = remember { mutableMapOf<Int, Int>() }

                val offsets = remember { mutableMapOf<String, Int>() }
                val heightMod: @Composable (Int) -> Modifier = { idx ->
                    Modifier.onGloballyPositioned { coords ->
                        println("DEBUG: Register height for block $idx to ${coords.size.height}")
                        blockHeights[idx] = coords.size.height
                    }
                }

                val anchorMod: @Composable (String) -> Modifier = { id ->
                    Modifier.onGloballyPositioned { coords ->
                        println("DEBUG: Heading $id at ${coords.positionInParent()}")
                        val y = coords.positionInParent().y
                        offsets[id] = y.toInt().coerceAtLeast(0)
                    }
                }
                val jumpTo: suspend (HeadingInfo) -> Unit = { info ->
                    blockHeights.filterKeys { it < (info.blockIndex ?: -1) }.values.sum()
                        .let { sum ->
                            val y = offsets.firstNotNullOfOrNull {
                                if (it.key.contains(info.text) || it.key.contains(info.anchor)) it.value else null
                            }?.plus(sum)

                            if (y != null) scroll.animateScrollTo(y.coerceAtMost(scroll.maxValue)) else println(
                                "DEBUG: No offset for ${info.text}/${info.anchor}"
                            )
                        }

                }

                CompositionLocalProvider(
                    LocalAnchorModifier provides anchorMod,
                    LocalBlockHeightModifier provides heightMod
                ) {
                    if (custom != null) {
                        val props = PageScaffoldProps(
                            headings = headings,
                            breadcrumbs = it.metadata[BreadcrumbsKey] ?: emptyList(),
                            content = { innerMod -> it.content(innerMod.verticalScroll(scroll)) },
                            jumpTo = jumpTo,
                        )
                        custom(props, mod)
                    } else {
                        Row(mod.fillMaxWidth()) {
                            if (config.enableToc && headings.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .weight(0.35f, fill = true),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Contents",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    headings.forEach { h ->
                                        val item = config.headingItem
                                        if (item != null) {
                                            // Respect user's custom item; do not force clickable.
                                            item(h)
                                        } else {
                                            DefaultHeadingItem(
                                                h,
                                                numbering = config.tocNumbering,
                                                onClick = { scope.launch { jumpTo(h) } }
                                            )
                                        }
                                    }
                                }
                                VerticalDivider(Modifier.fillMaxHeight())
                            }
                            Column(modifier = Modifier.weight(1f).verticalScroll(scroll)) {
                                it.content(Modifier)
                            }
                        }
                    }
                }
            }

            proceedWith(wrapped)
        }
    }

@Composable
private fun DefaultHeadingItem(
    h: HeadingInfo,
    numbering: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val style = when (h.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyMedium
    }
    val indent = (h.level - 1).coerceAtLeast(0) * 8
    val label = if (numbering) "• ${h.text}" else h.text
    val base = Modifier.padding(start = indent.dp)
    val clickable = onClick?.let { base.clickable(onClick = it) } ?: base
    Text(text = label, style = style, modifier = clickable)
}

package io.github.arashiyama11.composemark.plugin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
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
    public var toc: (@Composable (TocProps, Modifier) -> Unit)? = null
    public var injectHeadingIds: Boolean = false
    public var scrollState: ScrollState? = null
    public var scrollWithToc: Boolean = false
    public var tocPosition: TocPosition = TocPosition.Left

    public fun enableScroll(state: ScrollState = ScrollState(0), withToc: Boolean = false) {
        scrollState = state
        scrollWithToc = withToc
    }

    public fun headingItem(item: @Composable (HeadingInfo) -> Unit) {
        headingItem = item
    }

    public fun scaffold(scaffold: @Composable (PageScaffoldProps, Modifier) -> Unit) {
        this.scaffold = scaffold
    }

    public fun toc(toc: @Composable (TocProps, Modifier) -> Unit) {
        this.toc = toc
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
    val scrollState: ScrollState? = null,
)

public enum class TocPosition { Top, Left, Right }

public data class TocProps(
    val headings: List<HeadingInfo>,
    val numbering: Boolean,
    val jumpTo: suspend (HeadingInfo) -> Unit,
    val scrollState: ScrollState? = null,
    val position: TocPosition = TocPosition.Left,
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
            subject.content.path?.let { p -> subject.metadata[PagePathKey] = p }
            proceed()
        }

        onRenderBlocks {

            val headings =
                it.metadata[PageHeadingsKey] ?: parseHeadingsFromMarkdownSource(it.fullSource)

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
                val scroll = config.scrollState
                val scope = rememberCoroutineScope()

                // Anchor registry for BringIntoView strategy
                val requesters = remember { mutableMapOf<String, BringIntoViewRequester>() }
                val rects = remember { mutableMapOf<String, Rect>() }


                val anchorMod: @Composable (String) -> Modifier = { id ->
                    val requester = remember { BringIntoViewRequester() }
                    // Keep latest requester registered for this id
                    SideEffect { requesters[id] = requester }
                    Modifier
                        .bringIntoViewRequester(requester)
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInParent()
                            rects[id] = b
                        }
                }
                val jumpTo: suspend (HeadingInfo) -> Unit = { info ->
                    // Find a matching anchor key registered by renderer
                    val key = requesters.keys.firstOrNull { k ->
                        k.contains(info.anchor) || k.contains(info.text)
                    } ?: info.anchor

                    val rq = requesters[key]
                    if (rq != null) {
                        val rect = rects[key]
                        try {
                            if (rect != null) rq.bringIntoView(rect) else rq.bringIntoView()
                        } catch (t: Throwable) {
                        }
                    } else {
                    }
                }

                CompositionLocalProvider(
                    LocalAnchorModifier provides anchorMod,
                ) {
                    if (custom != null) {
                        val props = PageScaffoldProps(
                            headings = headings,
                            breadcrumbs = it.metadata[BreadcrumbsKey] ?: emptyList(),
                            content = { innerMod -> it.content(innerMod) },
                            jumpTo = jumpTo,
                            scrollState = scroll,
                        )
                        val container =
                            if (scroll != null && config.scrollWithToc) mod.verticalScroll(scroll) else mod
                        custom(props, container)
                    } else {
                        val headingsPresent = config.enableToc && headings.isNotEmpty()
                        // Common TOC composable (customizable via config.toc)
                        val TocComposable: @Composable (Modifier) -> Unit = { m ->
                            val customToc = config.toc
                            if (customToc != null) {
                                val props = TocProps(
                                    headings = headings,
                                    numbering = config.tocNumbering,
                                    jumpTo = jumpTo,
                                    scrollState = scroll,
                                    position = config.tocPosition,
                                )
                                customToc(props, m)
                            } else {
                                Column(
                                    modifier = m
                                        .padding(end = if (config.tocPosition == TocPosition.Left) 16.dp else 0.dp)
                                        .padding(start = if (config.tocPosition == TocPosition.Right) 16.dp else 0.dp),
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
                            }
                        }

                        val container =
                            if (scroll != null && config.scrollWithToc) mod.fillMaxWidth()
                                .verticalScroll(scroll) else mod.fillMaxWidth()

                        when (config.tocPosition) {
                            TocPosition.Top -> {
                                Column(container) {
                                    if (headingsPresent) {
                                        TocComposable(Modifier.fillMaxWidth())
                                        HorizontalDivider()
                                    }
                                    val contentModifier =
                                        if (scroll != null && !config.scrollWithToc) Modifier.verticalScroll(
                                            scroll
                                        ) else Modifier
                                    Column(modifier = contentModifier.fillMaxWidth()) {
                                        it.content(
                                            Modifier
                                        )
                                    }
                                }
                            }

                            TocPosition.Left -> {
                                Row(container) {
                                    if (headingsPresent) {
                                        Column(
                                            modifier = Modifier.weight(
                                                0.35f,
                                                fill = true
                                            )
                                        ) { TocComposable(Modifier.fillMaxWidth()) }
                                        VerticalDivider(Modifier.fillMaxHeight())
                                    }
                                    val contentModifier =
                                        if (scroll != null && !config.scrollWithToc) Modifier.weight(
                                            1f
                                        ).verticalScroll(scroll) else Modifier.weight(1f)
                                    Column(modifier = contentModifier) { it.content(Modifier) }
                                }
                            }

                            TocPosition.Right -> {
                                Row(container) {
                                    val contentModifier =
                                        if (scroll != null && !config.scrollWithToc) Modifier.weight(
                                            1f
                                        ).verticalScroll(scroll) else Modifier.weight(1f)
                                    Column(modifier = contentModifier) { it.content(Modifier) }
                                    if (headingsPresent) {
                                        VerticalDivider(Modifier.fillMaxHeight())
                                        Column(
                                            modifier = Modifier.weight(
                                                0.35f,
                                                fill = true
                                            )
                                        ) { TocComposable(Modifier.fillMaxWidth()) }
                                    }
                                }
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

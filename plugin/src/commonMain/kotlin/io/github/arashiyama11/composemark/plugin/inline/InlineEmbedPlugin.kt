package io.github.arashiyama11.composemark.plugin.inline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.arashiyama11.composemark.core.Block
import io.github.arashiyama11.composemark.core.BlockItem
import io.github.arashiyama11.composemark.core.ComposeMarkPlugin
import io.github.arashiyama11.composemark.core.PreProcessorMetadataKey
import io.github.arashiyama11.composemark.core.composeMarkPlugin

public object InlineEmbedDefaults {
    public val defaultWidth: TextUnit = 16.sp
    public val defaultHeight: TextUnit = 18.sp
    public val defaultMetrics: InlineTextMetrics = InlineTextMetrics(defaultWidth, defaultHeight)
}

public class InlineEmbedPluginConfig {
    public var defaultWidth: TextUnit = InlineEmbedDefaults.defaultWidth
    public var defaultHeight: TextUnit = InlineEmbedDefaults.defaultHeight
}

@Immutable
public data class InlineTextMetrics(
    val width: TextUnit,
    val height: TextUnit,
)

@Immutable
public data class InlineSlotConfig(
    val id: String,
    val attrs: Map<String, String?>,
    val blockIndex: Int,
)

public val InlinePlaceholderRegex: Regex =
    Regex("""\[cm-inline:([A-Za-z0-9_\-]+)]""")

public val InlinePlaceholdersKey: PreProcessorMetadataKey<SnapshotStateMap<String, InlineSlotConfig>> =
    PreProcessorMetadataKey("inline.placeholders")

public val InlinePlaceholdersQueueKey: PreProcessorMetadataKey<List<InlineSlotConfig>> =
    PreProcessorMetadataKey("inline.placeholders.queue")

public val InlineContentMapKey: PreProcessorMetadataKey<Map<String, InlineTextContent>> =
    PreProcessorMetadataKey("inline.content.map")


public val LocalInlinePlans: ProvidableCompositionLocal<SnapshotStateMap<String, InlineSlotConfig>> =
    staticCompositionLocalOf { mutableStateMapOf() }


public val LocalInlineChildren: ProvidableCompositionLocal<SnapshotStateMap<String, @Composable () -> Unit>> =
    compositionLocalOf<SnapshotStateMap<String, @Composable () -> Unit>> {
        error("No LocalInlineChildren provided")
    }

public val LocalInlineDefaultTextMetrics: ProvidableCompositionLocal<InlineTextMetrics> =
    staticCompositionLocalOf { InlineEmbedDefaults.defaultMetrics }

public val InlineEmbedPlugin: ComposeMarkPlugin<InlineEmbedPluginConfig> =
    composeMarkPlugin(::InlineEmbedPluginConfig) { config ->
        onBlockListPreProcess { subject ->
            val blocks = subject.data.blocks.toMutableList()
            if (blocks.isEmpty()) {
                proceed()
                return@onBlockListPreProcess
            }

            val newBlocks = mutableListOf<BlockItem>()
            val frontBlocks = mutableListOf<BlockItem>()
            val plans = mutableStateMapOf<String, InlineSlotConfig>()
            val queue = mutableListOf<InlineSlotConfig>()

            fun mergeMarkdownAt(index: Int, appendSource: String): BlockItem =
                mergeMarkdownBlock(newBlocks[index], appendSource, subject.data.path)

            val sections = parseComposableSections(subject.data.fullSource)
            val iter = subject.data.blocks.iterator()
            var inlineCounter = 0
            var lastMarkdownIndexInNew = -1

            sections.forEachIndexed { idx, sec ->
                val next = if (iter.hasNext()) iter.next() else return@forEachIndexed
                if (sec.markdown != null) {
                    if (lastMarkdownIndexInNew == newBlocks.lastIndex && newBlocks.isNotEmpty()) {
                        newBlocks[lastMarkdownIndexInNew] =
                            mergeMarkdownAt(lastMarkdownIndexInNew, next.source)
                    } else {
                        lastMarkdownIndexInNew = newBlocks.size
                        newBlocks += next
                    }
                } else {
                    val isInline = sec.attrs?.get("inline")?.lowercase() == "true"
                    if (isInline) {
                        inlineCounter += 1
                        val id = "cm_inline_" + inlineCounter.toString().padStart(3, '0')

                        var targetIndex = lastMarkdownIndexInNew
                        if (targetIndex == -1) {
                            val empty = Block.markdown("", subject.data.path)
                            newBlocks.add(0, empty)
                            targetIndex = 0
                            lastMarkdownIndexInNew = 0
                        }
                        newBlocks[targetIndex] =
                            mergeMarkdownAt(targetIndex, "[cm-inline:$id]")

                        val plan = InlineSlotConfig(
                            id = id,
                            attrs = sec.attrs,
                            blockIndex = frontBlocks.size,
                        )
                        plans[id] = plan
                        queue += plan

                        frontBlocks += next
                    } else {
                        newBlocks += next
                    }
                }
            }


            while (iter.hasNext()) {
                val remain = iter.next()
                if (lastMarkdownIndexInNew == newBlocks.lastIndex && newBlocks.isNotEmpty()) {
                    newBlocks[lastMarkdownIndexInNew] =
                        mergeMarkdownAt(lastMarkdownIndexInNew, remain.source)
                } else {
                    lastMarkdownIndexInNew = newBlocks.size
                    newBlocks += remain
                }
            }

            subject.metadata[InlinePlaceholdersKey] = plans
            subject.metadata[InlinePlaceholdersQueueKey] = queue

            val updated = subject.data.copy(blocks = frontBlocks + newBlocks)
            proceedWith(subject.copy(data = updated))
            return@onBlockListPreProcess
        }

        onRenderBlocks { subject ->
            val plans = subject.metadata[InlinePlaceholdersKey] ?: mutableStateMapOf()
            val wrapped = subject.copy { mod: Modifier ->
                val registry = remember { mutableStateMapOf<String, @Composable () -> Unit>() }
                val width = resolveWidth(config.defaultWidth)
                val height = resolveHeight(config.defaultHeight)
                val metrics = InlineTextMetrics(width, height)

                CompositionLocalProvider(
                    LocalInlinePlans provides plans,
                    LocalInlineChildren provides registry,
                    LocalInlineDefaultTextMetrics provides metrics,
                ) {
                    subject.content(mod)
                }
            }
            proceedWith(wrapped)
        }

        onRenderComposableBlock { subject ->
            val result = subject.copy { modifier ->
                val plans = LocalInlinePlans.current
                val isInline =
                    subject.renderContext.attrs["inline"]?.equals("true", ignoreCase = true) == true
                val plan =
                    plans.values.firstOrNull { it.blockIndex == subject.renderContext.blockIndex }

                if (isInline && plan != null) {
                    val reg = LocalInlineChildren.current
                    DisposableEffect(plan.id) {
                        reg[plan.id] = { subject.content(modifier) }
                        onDispose { reg.remove(plan.id) }
                    }
                    return@copy
                }
                subject.content(modifier)
            }

            proceedWith(result)
        }
    }

public fun mapVerticalAlign(value: String?): PlaceholderVerticalAlign = when (value?.lowercase()) {
    "baseline" -> PlaceholderVerticalAlign.AboveBaseline
    "center", "middle", "textcenter" -> PlaceholderVerticalAlign.TextCenter
    "top" -> PlaceholderVerticalAlign.Top
    "bottom" -> PlaceholderVerticalAlign.Bottom
    else -> PlaceholderVerticalAlign.AboveBaseline
}

public sealed interface SizeSpec {
    public data object Content : SizeSpec
    public data class Sp(val value: Float) : SizeSpec
    public data class Em(val value: Float) : SizeSpec
    public data class Dp(val value: Float) : SizeSpec
}

public fun parseSizeSpec(raw: String?): SizeSpec? {
    val v = raw?.trim()?.lowercase().orEmpty()
    if (v.isEmpty()) return null
    if (v == "content") return SizeSpec.Content
    val dot = v.lastIndexOf('.')
    if (dot > 0 && dot < v.length - 1) {
        val num = v.substring(0, dot).toFloatOrNull()
        val unit = v.substring(dot + 1)
        if (num != null) when (unit) {
            "sp" -> return SizeSpec.Sp(num)
            "em" -> return SizeSpec.Em(num)
            "dp" -> return SizeSpec.Dp(num)
        }
    }

    return when {
        v.startsWith("sp:") -> v.removePrefix("sp:").toFloatOrNull()?.let { SizeSpec.Sp(it) }
        v.startsWith("em:") -> v.removePrefix("em:").toFloatOrNull()?.let { SizeSpec.Em(it) }
        v.startsWith("dp:") -> v.removePrefix("dp:").toFloatOrNull()?.let { SizeSpec.Dp(it) }
        else -> null
    }
}

context(density: Density)
public fun resolveTextUnitOrNull(value: String?): TextUnit? {
    return when (val spec = parseSizeSpec(value)) {
        null -> null
        SizeSpec.Content -> null
        is SizeSpec.Sp -> spec.value.sp
        is SizeSpec.Em -> spec.value.em
        is SizeSpec.Dp -> dpToSp(spec.value.dp)
    }
}

context(density: Density)
private fun dpToSp(dp: Dp): TextUnit {
    return with(density) { dp.toSp() }
}

context(density: Density)
public fun buildInlineTextContent(
    plan: InlineSlotConfig,
    children: @Composable () -> Unit,
    defaultWidth: TextUnit = InlineEmbedDefaults.defaultWidth,
    defaultHeight: TextUnit = InlineEmbedDefaults.defaultHeight,
): InlineTextContent {
    val widthAttr = plan.attrs["width"]
    val heightAttr = plan.attrs["height"]
    val valign = mapVerticalAlign(plan.attrs["valign"])
    val widthTu = resolveTextUnitOrNull(widthAttr)
    val heightTu = resolveTextUnitOrNull(heightAttr)
    val effectiveWidth = resolveWidth(widthTu ?: defaultWidth)
    val resolvedHeight = resolveHeight(heightTu ?: defaultHeight)

    return InlineTextContent(
        placeholder = Placeholder(effectiveWidth, resolvedHeight, valign)
    ) {

        val density = LocalDensity.current
        val wDp = with(density) { effectiveWidth.toPx().dp }
        Box(Modifier.width(wDp)) { children() }
    }
}

internal fun resolveWidth(width: TextUnit): TextUnit {
    return width.takeIf { it != TextUnit.Unspecified } ?: InlineEmbedDefaults.defaultWidth
}

internal fun resolveHeight(
    height: TextUnit,
): TextUnit {
    return height.takeIf { it != TextUnit.Unspecified } ?: InlineEmbedDefaults.defaultHeight
}

@Composable
public fun rememberInlineEmbedContent(): SnapshotStateMap<String, InlineTextContent> {
    val plans = LocalInlinePlans.current
    val reg = LocalInlineChildren.current
    val density = LocalDensity.current
    val defaultMetrics = LocalInlineDefaultTextMetrics.current
    val metricsWidth = defaultMetrics.width
    val metricsHeight = defaultMetrics.height

    val stateMap = remember { mutableStateMapOf<String, InlineTextContent>() }

    val latestReg = rememberUpdatedState(reg)

    val target = remember(plans, density, metricsWidth, metricsHeight) {
        with(density) {
            buildMap {
                for (plan in plans.values) {
                    put(
                        plan.id,
                        buildInlineTextContent(
                            plan = plan,
                            defaultWidth = metricsWidth,
                            defaultHeight = metricsHeight,
                            children = {
                                latestReg.value[plan.id]?.invoke()
                            },
                        )
                    )
                }
            }
        }
    }

    SideEffect {
        val toRemove = stateMap.keys - target.keys
        toRemove.forEach {
            stateMap.remove(it)
        }
        for ((k, v) in target) {
            if (stateMap[k] !== v) stateMap[k] = v
        }
    }

    return stateMap
}

public fun AnnotatedString.Builder.annotateInlineEmbedContent(content: String): Boolean {
    val matches = InlinePlaceholderRegex.findAll(content).toList()
    if (matches.isEmpty()) return false
    var i = 0
    matches.forEach { m ->
        if (m.range.first > i) append(content.substring(i, m.range.first))
        val id = m.groupValues[1]
        appendInlineContent(id, "[loading...]")
        i = m.range.last + 1
    }
    if (i < content.length) append(content.substring(i))
    return true
}

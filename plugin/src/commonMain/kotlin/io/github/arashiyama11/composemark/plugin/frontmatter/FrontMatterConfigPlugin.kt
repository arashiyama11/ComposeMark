package io.github.arashiyama11.composemark.plugin.frontmatter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.Block
import io.github.arashiyama11.composemark.core.BlockItem
import io.github.arashiyama11.composemark.core.ComposeMarkPlugin
import io.github.arashiyama11.composemark.core.ImmutablePreProcessorMetadata
import io.github.arashiyama11.composemark.core.PipelinePriority
import io.github.arashiyama11.composemark.core.PreProcessorPipelineContent
import io.github.arashiyama11.composemark.core.composeMarkPlugin

public class FrontMatterConfigPluginConfig {
    internal var registry: FrontMatterConfigRegistry = FrontMatterConfigRegistry.Empty

    public fun configureDecoderRegistry(block: FrontMatterConfigRegistry.Builder.() -> Unit) {
        registry = FrontMatterConfigRegistry.build(block)
    }

    public fun setDecoderRegistry(registry: FrontMatterConfigRegistry) {
        this.registry = registry
    }

    public var frontMatterSurface: @Composable (ImmutablePreProcessorMetadata, Modifier, @Composable (Modifier) -> Unit) -> Unit =
        { _, m, content -> content(m) }

    public fun frontMatterSurface(
        wrapper: @Composable (ImmutablePreProcessorMetadata, Modifier, @Composable (Modifier) -> Unit) -> Unit
    ) {
        this.frontMatterSurface = wrapper
    }
}

public val FrontMatterConfigPlugin: ComposeMarkPlugin<FrontMatterConfigPluginConfig> =
    composeMarkPlugin(::FrontMatterConfigPluginConfig) { config ->
        onBlockListPreProcess(priority = PipelinePriority.High) { subject ->
            val parsed = parseFrontMatter(subject.data.fullSource) ?: run {
                proceed()
                return@onBlockListPreProcess
            }

            val store = FrontMatterConfigStore(
                section = parsed.section,
                defaultRegistry = config.registry,
            )

            subject.metadata.storeFrontMatterConfig(store)

            val updatedBlocks = stripFrontMatter(subject.data.blocks, parsed.consumedText)
            val updatedContext = subject.data.copy(
                blocks = updatedBlocks,
                fullSource = parsed.body,
            )

            proceedWith(
                PreProcessorPipelineContent(
                    data = updatedContext,
                    metadata = subject.metadata,
                )
            )
        }

        onRenderBlocks { subject ->
            proceedWith(subject.copy { modifier ->
                config.frontMatterSurface(subject.metadata.snapshot(), modifier) {
                    subject.content(it)
                }
            })
        }
    }

internal fun stripFrontMatter(blocks: List<BlockItem>, consumed: String): List<BlockItem> {
    if (blocks.isEmpty() || consumed.isEmpty()) return blocks
    val first = blocks.first()
    val remaining = first.source.removePrefix(consumed)
    if (remaining == first.source) return blocks

    val newBlocks = ArrayList<BlockItem>(blocks.size)
    if (remaining.isNotEmpty()) {
        newBlocks += Block.markdown(remaining, path = null)
    }
    if (blocks.size > 1) {
        newBlocks.addAll(blocks.drop(1))
    }
    return newBlocks
}


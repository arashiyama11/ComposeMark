package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

public data class PreProcessorPipelineContent<T>(
    val data: T,
    val metadata: PreProcessorMetadata,
)

public class PreProcessorMetadata {
    private val container: MutableMap<PreProcessorMetadataKey<*>, Any?> = mutableMapOf()

    public operator fun <T> set(key: PreProcessorMetadataKey<T>, value: T) {
        container[key] = value
    }

    public operator fun <T> get(key: PreProcessorMetadataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return container[key] as? T
    }

    public fun snapshot(): ImmutablePreProcessorMetadata =
        ImmutablePreProcessorMetadata(container.toMap())
}

@Immutable
public class ImmutablePreProcessorMetadata internal constructor(
    private val container: Map<PreProcessorMetadataKey<*>, Any?>
) {
    public operator fun <T> get(key: PreProcessorMetadataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return container[key] as? T
    }
}

public class PreProcessorMetadataKey<T>(public val name: String)

public data class ComposablePipelineContent(
    val metadata: PreProcessorMetadata,
    val renderContext: RenderContext,
    val content: @Composable (Modifier) -> Unit
)

public data class MarkdownBlockContext(
    val fullSource: String,
    val path: String? = null,
    val blockIndex: Int,
    val totalBlocks: Int,
)

public data class MarkdownPipelineContent(
    val context: MarkdownBlockContext,
    val metadata: PreProcessorMetadata,
    val content: @Composable (MarkdownProperty) -> Unit
)

public data class RenderContext(
    val source: String,
    val fullSource: String,
    val path: String? = null,
    val blockIndex: Int,
    val totalBlocks: Int,
    val attrs: Map<String, String?> = emptyMap(),
)

public val RenderContext.metadata: ImmutablePreProcessorMetadata?
    @Composable
    get() = LocalPreProcessorMetadata.current

public data class BlocksProcessorContext(
    val blocks: List<BlockItem>,
    val fullSource: String,
    val path: String? = null,
)

public val LocalPreProcessorMetadata: ProvidableCompositionLocal<ImmutablePreProcessorMetadata?> =
    staticCompositionLocalOf { null }

public abstract class ComposeMark(private val renderer: MarkdownRenderer) {
    public val markdownBlockPreProcessorPipeline: Pipeline<PreProcessorPipelineContent<RenderContext>> =
        Pipeline()
    public val composableBlockPreProcessorPipeline: Pipeline<PreProcessorPipelineContent<RenderContext>> =
        Pipeline()
    public val blockListPreProcessorPipeline: Pipeline<PreProcessorPipelineContent<BlocksProcessorContext>> =
        Pipeline()
    public val renderMarkdownBlockPipeline: Pipeline<MarkdownPipelineContent> = Pipeline()
    public val renderComposableBlockPipeline: Pipeline<ComposablePipelineContent> = Pipeline()
    public val renderBlocksPipeline: Pipeline<ComposablePipelineContent> = Pipeline()

    public abstract fun setup()

    init {
        setup()
    }

    public fun <TBuilder> install(
        composeMark: ComposeMarkPlugin<TBuilder>,
        configure: TBuilder.() -> Unit = {}
    ) {
        composeMark.install(this, configure)
    }

    @Composable
    public fun RenderMarkdownBlock(
        context: RenderContext,
        modifier: Modifier = Modifier,
    ) {
        val result = remember(context.path, context.source) {

            val processed =
                markdownBlockPreProcessorPipeline.execute(
                    PreProcessorPipelineContent(
                        context,
                        PreProcessorMetadata(),
                    )
                )


            val renderSubject = MarkdownPipelineContent(
                metadata = processed.metadata,
                context = MarkdownBlockContext(
                    fullSource = processed.data.fullSource,
                    path = processed.data.path,
                    blockIndex = processed.data.blockIndex,
                    totalBlocks = processed.data.totalBlocks,
                ),
            ) { mp ->
                mp.Render()
            }

            renderMarkdownBlockPipeline.execute(renderSubject)
        }
        CompositionLocalProvider(LocalPreProcessorMetadata provides result.metadata.snapshot()) {
            result.content(renderer.rememberMarkdownProperty(context.source, modifier))
        }
    }

    @Composable
    public fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        val currentContent = rememberUpdatedState(content)

        val result = remember(context.path, context.source) {
            val subject = PreProcessorPipelineContent(
                data = context,
                metadata = PreProcessorMetadata(),
            )

            val processed = composableBlockPreProcessorPipeline.execute(subject)

            val renderSubject =
                ComposablePipelineContent(
                    processed.metadata,
                    context,
                ) { mod ->
                    renderer.RenderComposableBlock(context, mod) {
                        currentContent.value()
                    }
                }
            renderComposableBlockPipeline.execute(renderSubject)
        }
        CompositionLocalProvider(LocalPreProcessorMetadata provides result.metadata.snapshot()) {
            result.content(modifier)
        }
    }

    @Composable
    public fun RenderBlocks(
        blocks: List<BlockItem>,
        modifier: Modifier = Modifier,
        path: String? = null,
        fullSource: String? = null,
    ) {
        if (blocks.isEmpty()) return

        val computedFullSource = remember(blocks) { blocks.joinToString("\n") { it.source } }

        val blocksProcessed = remember(blocks, path, fullSource) {
            val subject = PreProcessorPipelineContent(
                data = BlocksProcessorContext(
                    blocks = blocks,
                    fullSource = fullSource ?: computedFullSource,
                    path = path,
                ),
                metadata = PreProcessorMetadata(),
            )
            blockListPreProcessorPipeline.execute(subject)
        }

        val result = remember(blocksProcessed.data) {
            val subject = ComposablePipelineContent(
                metadata = blocksProcessed.metadata,
                renderContext = RenderContext(
                    source = blocksProcessed.data.fullSource,
                    fullSource = blocksProcessed.data.fullSource,
                    path = blocksProcessed.data.path,
                    blockIndex = 0,
                    totalBlocks = blocksProcessed.data.blocks.size,
                ),
            ) { mod ->
                val entries = blocksProcessed.data.blocks.mapIndexed { i, item ->
                    val meta = (item as? BlockItemMeta)
                    val resolvedPath = resolvePath(meta?.explicitPath, blocksProcessed.data.path)
                    val ctx = RenderContext(
                        source = item.source,
                        fullSource = blocksProcessed.data.fullSource,
                        path = resolvedPath,
                        blockIndex = i,
                        totalBlocks = blocksProcessed.data.blocks.size,
                        attrs = meta?.attrs ?: emptyMap(),
                    )
                    BlockEntry(ctx) { childMod ->
                        item.Render(this, ctx, childMod)
                    }
                }
                renderer.BlockContainer(mod, entries)
            }
            renderBlocksPipeline.execute(subject)
        }

        CompositionLocalProvider(LocalPreProcessorMetadata provides result.metadata.snapshot()) {
            result.content(modifier)
        }
    }
}

public interface BlockItem {
    public val source: String

    @Composable
    public fun Render(composeMark: ComposeMark, context: RenderContext, modifier: Modifier)

    public companion object {
        public operator fun invoke(
            source: String,
            render: @Composable (composeMark: ComposeMark, context: RenderContext, modifier: Modifier) -> Unit
        ): BlockItem = object : BlockItem {
            override val source: String = source

            @Composable
            override fun Render(
                composeMark: ComposeMark,
                context: RenderContext,
                modifier: Modifier
            ) {
                render(composeMark, context, modifier)
            }
        }
    }
}

internal interface BlockItemMeta {
    val attrs: Map<String, String?>
    val explicitPath: String?
}

public object Block {
    public fun markdown(source: String, path: String?): BlockItem =
        object : BlockItem, BlockItemMeta {
            override val source: String = source
            override val attrs: Map<String, String?> = emptyMap()
            override val explicitPath: String? = path

            @Composable
            override fun Render(
                composeMark: ComposeMark,
                context: RenderContext,
                modifier: Modifier
            ) {
                composeMark.RenderMarkdownBlock(context.copy(path = path), modifier)
            }
        }

    public fun composable(
        source: String,
        path: String? = null,
        attrs: Map<String, String?> = emptyMap(),
        content: @Composable () -> Unit,
    ): BlockItem = object : BlockItem, BlockItemMeta {
        override val source: String = source
        override val attrs: Map<String, String?> = attrs
        override val explicitPath: String? = path

        @Composable
        override fun Render(
            composeMark: ComposeMark,
            context: RenderContext,
            modifier: Modifier
        ) {
            composeMark.RenderComposableBlock(
                context.copy(path = path, attrs = attrs),
                modifier
            ) { content() }
        }
    }
}

internal fun resolvePath(explicit: String?, inherited: String?): String? = explicit ?: inherited

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
    val content: T,
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
    val source: String,
    val content: @Composable (Modifier) -> Unit
)

public data class RenderContext(
    val source: String,
    val path: String? = null,
    val blockIndex: Int,
    val totalBlocks: Int,
)

public data class BlocksProcessorContext(
    val blocks: List<BlockItem>,
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
    public val renderMarkdownBlockPipeline: Pipeline<ComposablePipelineContent> = Pipeline()
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


            val renderSubject =
                ComposablePipelineContent(processed.metadata, context.source) { mod ->
                    renderer.RenderMarkdownBlock(processed.content, mod)
                }

            renderMarkdownBlockPipeline.execute(renderSubject)
        }
        CompositionLocalProvider(LocalPreProcessorMetadata provides result.metadata.snapshot()) {
            result.content(modifier)
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
                content = context,
                metadata = PreProcessorMetadata(),
            )

            val processed = composableBlockPreProcessorPipeline.execute(subject)

            val renderSubject =
                ComposablePipelineContent(processed.metadata, context.source) { mod ->
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
        path: String? = null
    ) {
        if (blocks.isEmpty()) return

        val blocksProcessed = remember(blocks, path) {
            val subject = PreProcessorPipelineContent(
                content = BlocksProcessorContext(blocks, path),
                metadata = PreProcessorMetadata(),
            )
            blockListPreProcessorPipeline.execute(subject)
        }

        val source = remember(blocks) { blocks.joinToString("\n") { it.source } }

        val result = remember(blocksProcessed.content) {
            val subject = ComposablePipelineContent(blocksProcessed.metadata, source) { mod ->
                val contents = blocksProcessed.content.blocks.mapIndexed { i, item ->
                    @Composable {
                        val ctx = RenderContext(
                            source = item.source,
                            path = blocksProcessed.content.path,
                            blockIndex = i,
                            totalBlocks = blocksProcessed.content.blocks.size,
                        )
                        item.Render(this, ctx, Modifier)
                    }
                }
                renderer.BlockContainer(mod, contents)
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

public object Block {
    public fun markdown(source: String, path: String?): BlockItem =
        BlockItem(source) { cm, ctx, modifier ->
            cm.RenderMarkdownBlock(ctx.copy(path = path), modifier)
        }

    public fun composable(
        source: String,
        path: String? = null,
        content: @Composable () -> Unit,
    ): BlockItem = BlockItem(source) { cm, ctx, modifier ->
        cm.RenderComposableBlock(ctx.copy(path = path), modifier) { content() }
    }
}

internal fun resolvePath(explicit: String?, inherited: String?): String? = explicit ?: inherited

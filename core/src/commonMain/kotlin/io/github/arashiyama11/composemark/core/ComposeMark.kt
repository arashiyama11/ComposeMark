package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

public data class PreProcessorPipelineContent<T>(
    val content: T,
    val metadata: PreProcessorMetadata,
    val path: String? = null,
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
}

public class PreProcessorMetadataKey<T>(public val name: String)

public data class ComposablePipelineContent(
    val metadata: PreProcessorMetadata,
    val source: String,
    val content: @Composable (Modifier) -> Unit
)

public abstract class ComposeMark(private val renderer: MarkdownRenderer) {
    public val markdownBlockPreProcessorPipeline: Pipeline<PreProcessorPipelineContent<String>> =
        Pipeline()
    public val composableBlockPreProcessorPipeline: Pipeline<PreProcessorPipelineContent<String>> =
        Pipeline()
    public val blockListPreProcessorPipeline: Pipeline<PreProcessorPipelineContent<List<BlockItem>>> =
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
    public fun RenderMarkDownBlock(
        source: String,
        modifier: Modifier = Modifier,
        path: String? = null
    ) {
        remember(path, source) {
            val subject = PreProcessorPipelineContent(
                content = source,
                metadata = PreProcessorMetadata(),
                path = path
            )
            val processed = markdownBlockPreProcessorPipeline.execute(subject)

            val renderSubject = ComposablePipelineContent(processed.metadata, source) { mod ->
                renderer.RenderMarkdownBlock(mod, processed.path, processed.content)
            }

            renderMarkdownBlockPipeline.execute(renderSubject)
        }.content(modifier)
    }

    @Composable
    public fun RenderComposableBlock(
        source: String,
        modifier: Modifier = Modifier,
        path: String? = null,
        content: @Composable () -> Unit
    ) {
        val currentContent = rememberUpdatedState(content)

        remember(path, source) {
            val subject = PreProcessorPipelineContent(
                content = source,
                metadata = PreProcessorMetadata(),
                path = path
            )

            val processed = composableBlockPreProcessorPipeline.execute(subject)

            val renderSubject = ComposablePipelineContent(processed.metadata, source) { mod ->
                renderer.RenderComposableBlock(
                    mod,
                    processed.path,
                    processed.content
                ) { currentContent.value() }
            }
            renderComposableBlockPipeline.execute(renderSubject)
        }.content(modifier)
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
                content = blocks,
                metadata = PreProcessorMetadata(),
                path = path
            )
            blockListPreProcessorPipeline.execute(subject)
        }

        val source = remember(blocks) { blocks.joinToString("\n") { it.source } }

        remember(blocksProcessed.content, blocksProcessed.path) {
            val subject = ComposablePipelineContent(blocksProcessed.metadata, source) { mod ->
                val contents = blocksProcessed.content.map { item ->
                    @Composable { item.Render(this, blocksProcessed.path, Modifier) }
                }
                renderer.BlockContainer(mod, contents)
            }
            renderBlocksPipeline.execute(subject)
        }.content(modifier)
    }
}

public interface BlockItem {
    public val source: String

    @Composable
    public fun Render(composeMark: ComposeMark, path: String?, modifier: Modifier)

    public companion object {
        public operator fun invoke(
            source: String,
            render: @Composable (composeMark: ComposeMark, path: String?, modifier: Modifier) -> Unit
        ): BlockItem = object : BlockItem {
            override val source: String = source

            @Composable
            override fun Render(composeMark: ComposeMark, path: String?, modifier: Modifier) {
                render(composeMark, path, modifier)
            }
        }
    }
}

public object Block {
    public fun markdown(source: String, path: String? = null): BlockItem =
        BlockItem(source) { cm, p, modifier ->
            cm.RenderMarkDownBlock(source, modifier, resolvePath(path, p))
        }

    public fun composable(
        source: String,
        path: String? = null,
        content: @Composable () -> Unit,
    ): BlockItem = BlockItem(source) { cm, p, modifier ->
        cm.RenderComposableBlock(source, modifier, resolvePath(path, p)) { content() }
    }
}

internal fun resolvePath(explicit: String?, inherited: String?): String? = explicit ?: inherited

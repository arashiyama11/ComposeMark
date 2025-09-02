package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public data class PreProcessorPipelineContent(
    val markdown: String,
    val metadata: PreProcessorMetadata
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

public data class RenderPipelineContent(
    val metadata: PreProcessorMetadata,
    val content: @Composable () -> Unit
)

public data class ComposablePipelineContent(
    val metadata: PreProcessorMetadata,
    val content: @Composable () -> Unit
)

public abstract class ComposeMark(private val renderer: MarkdownRenderer) {
    public val markdownPreProcessorPipeline: Pipeline<PreProcessorPipelineContent> = Pipeline()

    public val composablePreProcessorPipeline: Pipeline<PreProcessorPipelineContent> = Pipeline()
    public val renderPipeline: Pipeline<RenderPipelineContent> = Pipeline()
    public val composablePipeline: Pipeline<ComposablePipelineContent> = Pipeline()

    public abstract fun setup()

    init {
        setup()
    }

    public fun <TBuilder> install(
        plugin: ComposeMarkPlugin<TBuilder>,
        configure: TBuilder.() -> Unit = {}
    ) {
        plugin.install(this, configure)
    }

    @Composable
    public fun Render(
        modifier: Modifier,
        path: String?,
        source: String
    ) {
        val subject = PreProcessorPipelineContent(
            markdown = source,
            metadata = PreProcessorMetadata()
        )
        val processed = markdownPreProcessorPipeline.execute(subject)

        val renderSubject = RenderPipelineContent(processed.metadata) {
            renderer.Render(modifier, path, processed.markdown)
        }
        renderPipeline.execute(renderSubject).content()
    }

    @Composable
    public fun RenderComposable(
        modifier: Modifier,
        source: String,
        content: @Composable () -> Unit
    ) {
        val subject = PreProcessorPipelineContent(
            markdown = source,
            metadata = PreProcessorMetadata()
        )
        val processed = composablePreProcessorPipeline.execute(subject)

        val renderSubject = ComposablePipelineContent(processed.metadata) {
            renderer.RenderComposable(modifier, processed.markdown, content)
        }
        composablePipeline.execute(renderSubject).content()
    }
}

public interface ComposeMarkPlugin<out TBuilder> {
    public fun install(scope: ComposeMark, block: TBuilder.() -> Unit = {})
}

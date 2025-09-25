package io.github.arashiyama11.composemark.core


public interface ComposeMarkPlugin<out TBuilder> {
    public fun install(composeMark: ComposeMark, buildConfig: TBuilder.() -> Unit = {})
}


public class ComposeMarkPluginScope(
    private val composeMark: ComposeMark
) {
    public fun onBlockListPreProcess(
        priority: PipelinePriority = PipelinePriority.Normal,
        order: Int = 0,
        block: PipelineInterceptor<PreProcessorPipelineContent<BlocksProcessorContext>>,
    ) {
        composeMark.blockListPreProcessorPipeline.intercept(priority, order, block)
    }

    public fun onComposableBlockPreProcess(
        priority: PipelinePriority = PipelinePriority.Normal,
        order: Int = 0,
        block: PipelineInterceptor<PreProcessorPipelineContent<RenderContext>>,
    ) {
        composeMark.composableBlockPreProcessorPipeline.intercept(priority, order, block)
    }

    public fun onMarkdownBlockPreProcess(
        priority: PipelinePriority = PipelinePriority.Normal,
        order: Int = 0,
        block: PipelineInterceptor<PreProcessorPipelineContent<RenderContext>>,
    ) {
        composeMark.markdownBlockPreProcessorPipeline.intercept(priority, order, block)
    }

    public fun onRenderMarkdownBlock(
        priority: PipelinePriority = PipelinePriority.Normal,
        order: Int = 0,
        block: PipelineInterceptor<MarkdownPipelineContent>,
    ) {
        composeMark.renderMarkdownBlockPipeline.intercept(priority, order, block)
    }

    public fun onRenderComposableBlock(
        priority: PipelinePriority = PipelinePriority.Normal,
        order: Int = 0,
        block: PipelineInterceptor<ComposablePipelineContent>,
    ) {
        composeMark.renderComposableBlockPipeline.intercept(priority, order, block)
    }

    public fun onRenderBlocks(
        priority: PipelinePriority = PipelinePriority.Normal,
        order: Int = 0,
        block: PipelineInterceptor<ComposablePipelineContent>,
    ) {
        composeMark.renderBlocksPipeline.intercept(priority, order, block)
    }
}

public fun <T> createComposeMarkPlugin(
    block: ComposeMarkPluginScope.(T.() -> Unit) -> Unit
): ComposeMarkPlugin<T> {
    return object : ComposeMarkPlugin<T> {
        override fun install(composeMark: ComposeMark, buildConfig: T.() -> Unit) {
            ComposeMarkPluginScope(composeMark).block(buildConfig)
        }
    }
}


public fun <T> composeMarkPlugin(
    initialConfigFactory: () -> T,
    install: ComposeMarkPluginScope.(T) -> Unit
): ComposeMarkPlugin<T> = createComposeMarkPlugin { buildConfig ->
    val config = initialConfigFactory().apply(buildConfig)
    this.install(config)
}

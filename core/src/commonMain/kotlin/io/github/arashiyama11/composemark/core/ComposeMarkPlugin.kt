package io.github.arashiyama11.composemark.core


public interface ComposeMarkPlugin<out TBuilder> {
    public fun install(composeMark: ComposeMark, buildConfig: TBuilder.() -> Unit = {})
}


public class ComposeMarkPluginScope(
    private val composeMark: ComposeMark
) {
    public fun onBlockListPreProcess(block: PipelineInterceptor<PreProcessorPipelineContent<BlocksProcessorContext>>) {
        composeMark.blockListPreProcessorPipeline.intercept(block)
    }

    public fun onComposableBlockPreProcess(block: PipelineInterceptor<PreProcessorPipelineContent<RenderContext>>) {
        composeMark.composableBlockPreProcessorPipeline.intercept(block)
    }

    public fun onMarkdownBlockPreProcess(block: PipelineInterceptor<PreProcessorPipelineContent<RenderContext>>) {
        composeMark.markdownBlockPreProcessorPipeline.intercept(block)
    }

    public fun onRenderMarkdownBlock(block: PipelineInterceptor<ComposablePipelineContent>) {
        composeMark.renderMarkdownBlockPipeline.intercept(block)
    }

    public fun onRenderComposableBlock(block: PipelineInterceptor<ComposablePipelineContent>) {
        composeMark.renderComposableBlockPipeline.intercept(block)
    }

    public fun onRenderBlocks(block: PipelineInterceptor<ComposablePipelineContent>) {
        composeMark.renderBlocksPipeline.intercept(block)
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

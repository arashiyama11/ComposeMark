package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderPipelineInterceptionTest {

    @Test
    fun `plugin interceptors modify render pipelines`() {
        val cm = RenderTestComposeMark()

        // onRenderMarkdownBlock
        val md = ComposablePipelineContent(PreProcessorMetadata(), source = "MD", fullSource = "MD") { }
        val mdProcessed = cm.renderMarkdownBlockPipeline.execute(md)
        assertEquals("MD-RMD", mdProcessed.source)
        assertEquals("rmdb", mdProcessed.metadata[RENDER_FLAG_KEY])

        // onRenderComposableBlock
        val cb = ComposablePipelineContent(PreProcessorMetadata(), source = "CB", fullSource = "CB") { }
        val cbProcessed = cm.renderComposableBlockPipeline.execute(cb)
        assertEquals("CB-RCB", cbProcessed.source)
        assertEquals("rcbb", cbProcessed.metadata[RENDER_FLAG_KEY])

        // onRenderBlocks
        val bl = ComposablePipelineContent(PreProcessorMetadata(), source = "B1\nB2", fullSource = "B1\nB2") { }
        val blProcessed = cm.renderBlocksPipeline.execute(bl)
        assertEquals("B1\nB2-RBL", blProcessed.source)
        assertEquals("rblb", blProcessed.metadata[RENDER_FLAG_KEY])
    }
}

private val RENDER_FLAG_KEY = PreProcessorMetadataKey<String>("render-flag")

private class RenderTestComposeMark : ComposeMark(RenderNoopRenderer) {
    override fun setup() {
        install(RenderInterceptPlugin)
    }
}

private object RenderNoopRenderer : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {}

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {}
}

private val RenderInterceptPlugin: ComposeMarkPlugin<Unit> = composeMarkPlugin({ Unit }) {
    onRenderMarkdownBlock { subject ->
        subject.metadata[RENDER_FLAG_KEY] = "rmdb"
        proceedWith(subject.copy(source = subject.source + "-RMD"))
    }
    onRenderComposableBlock { subject ->
        subject.metadata[RENDER_FLAG_KEY] = "rcbb"
        proceedWith(subject.copy(source = subject.source + "-RCB"))
    }
    onRenderBlocks { subject ->
        subject.metadata[RENDER_FLAG_KEY] = "rblb"
        proceedWith(subject.copy(source = subject.source + "-RBL"))
    }
}

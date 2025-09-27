package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderPipelineInterceptionTest {

    @Test
    fun `plugin interceptors modify render pipelines`() {
        val cm = RenderTestComposeMark()

        // onRenderMarkdownBlock
        val md = MarkdownPipelineContent(
            metadata = PreProcessorMetadata(),
            context = MarkdownBlockContext(
                fullSource = "MD",
                path = null,
                blockIndex = 0,
                totalBlocks = 1,
            ),
        ) { _ -> }
        val mdProcessed = cm.renderMarkdownBlockPipeline.execute(md)
        assertEquals("MD-RMD", mdProcessed.context.fullSource)
        assertEquals("rmdb", mdProcessed.metadata[RENDER_FLAG_KEY])

        // onRenderComposableBlock
        val cb = ComposablePipelineContent(
            metadata = PreProcessorMetadata(),
            renderContext = RenderContext(
                source = "CB",
                fullSource = "CB",
                path = null,
                blockIndex = 0,
                totalBlocks = 1,
            ),
        ) { _ -> }
        val cbProcessed = cm.renderComposableBlockPipeline.execute(cb)
        assertEquals("CB-RCB", cbProcessed.renderContext.source)
        assertEquals("rcbb", cbProcessed.metadata[RENDER_FLAG_KEY])

        // onRenderBlocks
        val bl = ComposablePipelineContent(
            metadata = PreProcessorMetadata(),
            renderContext = RenderContext(
                source = "B1\nB2",
                fullSource = "B1\nB2",
                path = null,
                blockIndex = 0,
                totalBlocks = 2,
            ),
        ) { _ -> }
        val blProcessed = cm.renderBlocksPipeline.execute(bl)
        assertEquals("B1\nB2-RBL", blProcessed.renderContext.source)
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
    override fun rememberMarkdownColors(): MarkdownColors = TODO("Not required for this test")

    @Composable
    override fun rememberMarkdownTypography(): MarkdownTypography = TODO("Not required for this test")

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
    }
}

private val RenderInterceptPlugin: ComposeMarkPlugin<Unit> = composeMarkPlugin({ Unit }) {
    onRenderMarkdownBlock { subject ->
        subject.metadata[RENDER_FLAG_KEY] = "rmdb"
        val updatedContext = subject.context.copy(
            fullSource = subject.context.fullSource + "-RMD"
        )
        proceedWith(subject.copy(context = updatedContext))
    }
    onRenderComposableBlock { subject ->
        subject.metadata[RENDER_FLAG_KEY] = "rcbb"
        proceedWith(subject.copy(renderContext = subject.renderContext.copy(source = subject.renderContext.source + "-RCB")))
    }
    onRenderBlocks { subject ->
        subject.metadata[RENDER_FLAG_KEY] = "rblb"
        proceedWith(subject.copy(renderContext = subject.renderContext.copy(source = subject.renderContext.source + "-RBL")))
    }
}

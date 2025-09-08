package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginInterceptionTest {

    @Test
    fun `plugin interceptors modify preprocessor pipelines`() {
        val cm = TestComposeMark()

        // markdownBlock pre-process is intercepted: content and path updated, metadata flagged
        val mdInitial = PreProcessorPipelineContent(
            content = RenderContext(
                source = "Hello",
                fullSource = "Hello",
                path = "original.md",
                blockIndex = 0,
                totalBlocks = 1,
            ),
            metadata = PreProcessorMetadata(),
        )
        val mdProcessed = cm.markdownBlockPreProcessorPipeline.execute(mdInitial)
        assertEquals("Hello-SUFFIX", mdProcessed.content.source)
        assertEquals("changed/path.md", mdProcessed.content.path)
        assertEquals("touched", mdProcessed.metadata[FLAG_KEY])

        // composableBlock pre-process is intercepted: content updated
        val compInitial = PreProcessorPipelineContent(
            content = RenderContext(
                source = "World",
                fullSource = "World",
                path = null,
                blockIndex = 0,
                totalBlocks = 1,
            ),
            metadata = PreProcessorMetadata(),
        )
        val compProcessed = cm.composableBlockPreProcessorPipeline.execute(compInitial)
        assertEquals("COMPOSABLE:World", compProcessed.content.source)

        // blockList pre-process is intercepted: an extra block is appended
        val blocksInitial = listOf(Block.markdown("First", path = null))
        val blProcessed = cm.blockListPreProcessorPipeline.execute(
            PreProcessorPipelineContent(
                content = BlocksProcessorContext(blocksInitial, fullSource = "First", path = null),
                metadata = PreProcessorMetadata(),
            )
        )
        assertEquals(2, blProcessed.content.blocks.size)
        assertEquals("First", blProcessed.content.blocks.first().source)
        assertEquals("AddedByPlugin", blProcessed.content.blocks.last().source)
    }
}

// Shared key so the test can assert metadata changes
private val FLAG_KEY = PreProcessorMetadataKey<String>("plugin-flag")

private class TestComposeMark : ComposeMark(FakeRenderer) {
    override fun setup() {
        install(TestPlugin) {
            suffix = "-SUFFIX"
            newPath = "changed/path.md"
        }
    }
}

private object FakeRenderer : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {}

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {}
}

private data class TestPluginConfig(
    var suffix: String = "",
    var newPath: String? = null,
)

private val TestPlugin = composeMarkPlugin(::TestPluginConfig) { cfg ->
    // Intercept markdown block pre-processing
    onMarkdownBlockPreProcess { subject ->
        subject.metadata[FLAG_KEY] = "touched"
        val c = subject.content
        proceedWith(subject.copy(content = c.copy(source = c.source + cfg.suffix, path = cfg.newPath)))
    }

    // Intercept composable block pre-processing
    onComposableBlockPreProcess { subject ->
        val c = subject.content
        proceedWith(subject.copy(content = c.copy(source = "COMPOSABLE:" + c.source)))
    }

    // Intercept block list pre-processing
    onBlockListPreProcess { subject ->
        val appended = subject.content.blocks + Block.markdown("AddedByPlugin", path = null)
        proceedWith(subject.copy(content = subject.content.copy(blocks = appended)))
    }
}

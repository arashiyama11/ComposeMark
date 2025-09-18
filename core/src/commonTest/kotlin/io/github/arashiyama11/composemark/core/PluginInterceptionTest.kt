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
            data = RenderContext(
                source = "Hello",
                fullSource = "Hello",
                path = "original.md",
                blockIndex = 0,
                totalBlocks = 1,
            ),
            metadata = PreProcessorMetadata(),
        )
        val mdProcessed = cm.markdownBlockPreProcessorPipeline.execute(mdInitial)
        assertEquals("Hello-SUFFIX", mdProcessed.data.source)
        assertEquals("changed/path.md", mdProcessed.data.path)
        assertEquals("touched", mdProcessed.metadata[FLAG_KEY])

        // composableBlock pre-process is intercepted: content updated
        val compInitial = PreProcessorPipelineContent(
            data = RenderContext(
                source = "World",
                fullSource = "World",
                path = null,
                blockIndex = 0,
                totalBlocks = 1,
            ),
            metadata = PreProcessorMetadata(),
        )
        val compProcessed = cm.composableBlockPreProcessorPipeline.execute(compInitial)
        assertEquals("COMPOSABLE:World", compProcessed.data.source)

        // blockList pre-process is intercepted: an extra block is appended
        val blocksInitial = listOf(Block.markdown("First", path = null))
        val blProcessed = cm.blockListPreProcessorPipeline.execute(
            PreProcessorPipelineContent(
                data = BlocksProcessorContext(blocksInitial, fullSource = "First", path = null),
                metadata = PreProcessorMetadata(),
            )
        )
        assertEquals(2, blProcessed.data.blocks.size)
        assertEquals("First", blProcessed.data.blocks.first().source)
        assertEquals("AddedByPlugin", blProcessed.data.blocks.last().source)
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
    override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {
    }

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
    }
}

private data class TestPluginConfig(
    var suffix: String = "",
    var newPath: String? = null,
)

private val TestPlugin = composeMarkPlugin(::TestPluginConfig) { cfg ->
    // Intercept markdown block pre-processing
    onMarkdownBlockPreProcess { subject ->
        subject.metadata[FLAG_KEY] = "touched"
        val c = subject.data
        proceedWith(subject.copy(data = c.copy(source = c.source + cfg.suffix, path = cfg.newPath)))
    }

    // Intercept composable block pre-processing
    onComposableBlockPreProcess { subject ->
        val c = subject.data
        proceedWith(subject.copy(data = c.copy(source = "COMPOSABLE:" + c.source)))
    }

    // Intercept block list pre-processing
    onBlockListPreProcess { subject ->
        val appended = subject.data.blocks + Block.markdown("AddedByPlugin", path = null)
        proceedWith(subject.copy(data = subject.data.copy(blocks = appended)))
    }
}

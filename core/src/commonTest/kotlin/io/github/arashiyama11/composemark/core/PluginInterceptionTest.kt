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
            content = "Hello",
            metadata = PreProcessorMetadata(),
            path = "original.md",
        )
        val mdProcessed = cm.markdownBlockPreProcessorPipeline.execute(mdInitial)
        assertEquals("Hello-SUFFIX", mdProcessed.content)
        assertEquals("changed/path.md", mdProcessed.path)
        assertEquals("touched", mdProcessed.metadata[FLAG_KEY])

        // composableBlock pre-process is intercepted: content updated
        val compInitial = PreProcessorPipelineContent(
            content = "World",
            metadata = PreProcessorMetadata(),
            path = null,
        )
        val compProcessed = cm.composableBlockPreProcessorPipeline.execute(compInitial)
        assertEquals("COMPOSABLE:World", compProcessed.content)

        // blockList pre-process is intercepted: an extra block is appended
        val blocksInitial = listOf(Block.markdown("First"))
        val blProcessed = cm.blockListPreProcessorPipeline.execute(
            PreProcessorPipelineContent(
                content = blocksInitial,
                metadata = PreProcessorMetadata(),
                path = null,
            )
        )
        assertEquals(2, blProcessed.content.size)
        assertEquals("First", blProcessed.content.first().source)
        assertEquals("AddedByPlugin", blProcessed.content.last().source)
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
    override fun RenderMarkdownBlock(modifier: Modifier, path: String?, source: String) {
    }

    @Composable
    override fun RenderComposableBlock(
        modifier: Modifier,
        path: String?,
        source: String,
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
        proceedWith(subject.copy(content = subject.content + cfg.suffix, path = cfg.newPath))
    }

    // Intercept composable block pre-processing
    onComposableBlockPreProcess { subject ->
        proceedWith(subject.copy(content = "COMPOSABLE:" + subject.content))
    }

    // Intercept block list pre-processing
    onBlockListPreProcess { subject ->
        val appended = subject.content + Block.markdown("AddedByPlugin")
        proceedWith(subject.copy(content = appended))
    }
}

package io.github.arashiyama11.composemark.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.Block
import io.github.arashiyama11.composemark.core.BlocksProcessorContext
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.PreProcessorMetadata
import io.github.arashiyama11.composemark.core.PreProcessorPipelineContent
import io.github.arashiyama11.composemark.core.RenderContext
import io.github.arashiyama11.composemark.plugin.inline.InlineEmbedPlugin
import io.github.arashiyama11.composemark.plugin.inline.InlinePlaceholdersKey
// parseSizeSpec/SizeSpec は本文内で FQCN を使用
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class TestRenderer : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {
    }

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit,
    ) {
        content()
    }
}

private class TestComposeMark : ComposeMark(TestRenderer()) {
    override fun setup() {}
}

class InlineEmbedPluginTest {

    @Test
    fun blockListPreProcess_moves_inline_to_head_and_merges_markdown() {
        val cm = TestComposeMark()
        cm.install(InlineEmbedPlugin) {}

        val inlineBlock = Block.composable(
            source = "Text(\"Hi\")",
            attrs = mapOf("inline" to "true")
        ) { }
        val blocks = listOf(
            Block.markdown("A", null),
            inlineBlock,
            Block.markdown("B", null),
        )
        val input = PreProcessorPipelineContent(
            data = BlocksProcessorContext(
                blocks = blocks,
                fullSource = "A<Composable inline=\"true\"></Composable>\nB",
                path = "/doc.md"
            ),
            metadata = PreProcessorMetadata(),
        )

        val out = cm.blockListPreProcessorPipeline.execute(input)

        val newBlocks = out.data.blocks
        assertEquals(2, newBlocks.size)
        assertEquals(inlineBlock.source, newBlocks[0].source)
        assertTrue(newBlocks[1].source.contains("[cm-inline:"))
        assertTrue(newBlocks[1].source.contains("A"))
        assertTrue(newBlocks[1].source.contains("B"))

        val plans = out.metadata[InlinePlaceholdersKey]
        assertNotNull(plans)
        assertEquals(1, plans!!.size)
        val id = plans.keys.first()
        assertTrue(id.startsWith("cm_inline_"))
        assertEquals(0, plans.values.first().blockIndex)
    }

    @Test
    fun normalize_size_values() {
        assertTrue(io.github.arashiyama11.composemark.plugin.inline.parseSizeSpec("content") is io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Content)

        val em = io.github.arashiyama11.composemark.plugin.inline.parseSizeSpec("1.2.em")
        assertTrue(em is io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Em && (em as io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Em).value == 1.2f)

        val dp = io.github.arashiyama11.composemark.plugin.inline.parseSizeSpec("24.dp")
        assertTrue(dp is io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Dp && (dp as io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Dp).value == 24f)

        val sp = io.github.arashiyama11.composemark.plugin.inline.parseSizeSpec("16.sp")
        assertTrue(sp is io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Sp && (sp as io.github.arashiyama11.composemark.plugin.inline.SizeSpec.Sp).value == 16f)
    }

    @Test
    fun blockListPreProcess_handles_inline_at_head_and_creates_leading_markdown() {
        val cm = TestComposeMark()
        cm.install(InlineEmbedPlugin) {}

        val inline = Block.composable(
            source = "Inline()",
            attrs = mapOf("inline" to "true")
        ) { }
        val blocks = listOf(
            inline,
            Block.markdown("A", null),
        )
        val input = PreProcessorPipelineContent(
            data = BlocksProcessorContext(
                blocks = blocks,
                fullSource = "<Composable inline=\"true\"></Composable>\nA",
                path = "/h.md"
            ),
            metadata = PreProcessorMetadata(),
        )
        val out = cm.blockListPreProcessorPipeline.execute(input)
        val newBlocks = out.data.blocks
        // 先頭は inline、本体 Markdown にプレースホルダが入る
        assertEquals(2, newBlocks.size)
        assertEquals(inline.source, newBlocks[0].source)
        assertTrue(newBlocks[1].source.startsWith("[cm-inline:"))
        assertTrue(newBlocks[1].source.contains("A"))
    }

    @Test
    fun blockListPreProcess_multiple_inlines_are_ordered_and_placeholders_collocated() {
        val cm = TestComposeMark()
        cm.install(InlineEmbedPlugin) {}

        val inl1 = Block.composable("I1()", attrs = mapOf("inline" to "true")) { }
        val inl2 = Block.composable("I2()", attrs = mapOf("inline" to "true")) { }
        val blocks = listOf(
            Block.markdown("A", null),
            inl1,
            inl2,
            Block.markdown("B", null),
        )
        val input = PreProcessorPipelineContent(
            data = BlocksProcessorContext(
                blocks = blocks,
                fullSource = "A<Composable inline=\"true\"></Composable><Composable inline=\"true\"></Composable>\nB",
                path = "/m.md"
            ),
            metadata = PreProcessorMetadata(),
        )
        val out = cm.blockListPreProcessorPipeline.execute(input)
        val newBlocks = out.data.blocks
        assertEquals(3, newBlocks.size)
        assertEquals(inl1.source, newBlocks[0].source)
        assertEquals(inl2.source, newBlocks[1].source)
        assertTrue(newBlocks[2].source.contains("[cm-inline:"))
        val placeholderCount = Regex("\\[cm-inline:").findAll(newBlocks[2].source).count()
        assertEquals(2, placeholderCount)
        assertTrue(newBlocks[2].source.contains("A"))
        assertTrue(newBlocks[2].source.contains("B"))

        val plans = out.metadata[InlinePlaceholdersKey]!!
        val (p1, p2) = plans.values.sortedBy { it.id }
        val set = plans.values.map { it.blockIndex }.toSet()
        assertEquals(setOf(0, 1), set)
    }

    @Test
    fun blockListPreProcess_merges_consecutive_markdown_around_removed_inline() {
        val cm = TestComposeMark()
        cm.install(InlineEmbedPlugin) {}

        val inline = Block.composable("I()", attrs = mapOf("inline" to "true")) { }
        val blocks = listOf(
            Block.markdown("A", null),
            inline,
            Block.markdown("B", null),
            Block.markdown("C", null),
        )
        val input = PreProcessorPipelineContent(
            data = BlocksProcessorContext(
                blocks = blocks,
                fullSource = "A<Composable inline=\"true\"></Composable>\nB\nC",
                path = "/k.md"
            ),
            metadata = PreProcessorMetadata(),
        )
        val out = cm.blockListPreProcessorPipeline.execute(input)
        val bs = out.data.blocks
        assertEquals(2, bs.size)
        val md = bs[1].source
        assertTrue(md.contains("A"))
        assertTrue(md.contains("[cm-inline:"))
        assertTrue(md.contains("B"))
        assertTrue(md.contains("C"))
    }

    @Test
    fun blockListPreProcess_keeps_non_inline_composable_in_place() {
        val cm = TestComposeMark()
        cm.install(InlineEmbedPlugin) {}

        val inline = Block.composable("I()", attrs = mapOf("inline" to "true")) { }
        val box = Block.composable("Box()", attrs = emptyMap()) { }
        val blocks = listOf(
            Block.markdown("A", null),
            inline,
            box,
            Block.markdown("B", null),
        )
        val input = PreProcessorPipelineContent(
            data = BlocksProcessorContext(
                blocks = blocks,
                fullSource = "A<Composable inline=\"true\"></Composable><Composable></Composable>\nB",
                path = "/n.md"
            ),
            metadata = PreProcessorMetadata(),
        )
        val out = cm.blockListPreProcessorPipeline.execute(input)
        val bs = out.data.blocks
        assertEquals(4, bs.size)
        assertEquals(inline.source, bs[0].source)
        assertTrue(bs[1].source.contains("A"))
        assertTrue(bs[1].source.contains("[cm-inline:"))
        assertEquals(box.source, bs[2].source)
        assertEquals("B", bs[3].source)
    }
}

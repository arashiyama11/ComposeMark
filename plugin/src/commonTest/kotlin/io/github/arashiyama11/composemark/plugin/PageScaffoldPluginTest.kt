package io.github.arashiyama11.composemark.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PageScaffoldPluginTest {

    @Test
    fun parseHeadings_returns_levels_and_anchors() {
        val md = """
            # Title One
            Intro
            ## Sub Section A
            Text
            ### Sub-Sub: B!
        """.trimIndent()

        val hs = parseHeadingsFromMarkdownSource(md)
        assertEquals(3, hs.size)
        assertEquals(1, hs[0].level)
        assertEquals("Title One", hs[0].text)
        assertEquals("title-one", hs[0].anchor)
        assertEquals(2, hs[1].level)
        assertEquals("sub-section-a", hs[1].anchor)
        assertEquals(3, hs[2].level)
        assertEquals("sub-sub-b", hs[2].anchor)
    }

    @Test
    fun computeBreadcrumbs_splits_and_strips_extension() {
        val crumbs = computeBreadcrumbsFromPath("/docs/guide/intro.md")
        assertEquals(3, crumbs.size)
        assertEquals("docs", crumbs[0].label)
        assertEquals("guide", crumbs[1].label)
        assertEquals("intro", crumbs[2].label)
        assertEquals("docs/guide/intro.md", crumbs.last().fullPath)
    }

    private class TestRenderer : MarkdownRenderer {
        @Composable
        override fun RenderMarkdownBlock(modifier: Modifier, path: String?, source: String) {}

        @Composable
        override fun RenderComposableBlock(
            modifier: Modifier,
            path: String?,
            source: String,
            content: @Composable () -> Unit,
        ) { content() }
    }

    private class TestComposeMark : ComposeMark(TestRenderer()) {
        override fun setup() {}
    }

    @Test
    fun blockListPreProcess_stores_path_in_metadata() {
        val cm = TestComposeMark()
        cm.install(PageScaffoldPlugin) {}

        val meta = PreProcessorMetadata()
        val input = PreProcessorPipelineContent(
            content = listOf<BlockItem>(),
            metadata = meta,
            path = "/docs/guide/intro.md"
        )
        val out = cm.blockListPreProcessorPipeline.execute(input)
        val stored = out.metadata[PagePathKey]
        assertEquals("/docs/guide/intro.md", stored)
    }

    @Test
    fun renderBlocks_sets_headings_and_breadcrumbs() {
        val cm = TestComposeMark()
        cm.install(PageScaffoldPlugin) {}

        val meta = PreProcessorMetadata().also { it[PagePathKey] = "/docs/guide/intro.md" }
        val subject = ComposablePipelineContent(
            metadata = meta,
            source = "# Title\n## Sub A",
        ) { }

        val result = cm.renderBlocksPipeline.execute(subject)
        val hs = result.metadata[PageHeadingsKey]
        val bc = result.metadata[BreadcrumbsKey]
        val applied = result.metadata[PageScaffoldAppliedKey]
        assertNotNull(hs)
        assertTrue(hs!!.size >= 2)
        assertEquals("title", hs[0].anchor)
        assertNotNull(bc)
        assertEquals("intro", bc!!.last().label)
        assertEquals(PageScaffoldApplied.Default, applied)
    }

    @Test
    fun markdown_preprocess_injects_ids() {
        val cm = TestComposeMark()
        cm.install(PageScaffoldPlugin) { injectHeadingIds = true }

        val input = PreProcessorPipelineContent(
            content = """
                # Title
                ## Sub
                ## Sub
                ### Another Title
            """.trimIndent(),
            metadata = PreProcessorMetadata(),
            path = null
        )

        val out = cm.markdownBlockPreProcessorPipeline.execute(input)
        val lines = out.content.lines()
        kotlin.test.assertEquals("# Title {#title}", lines[0])
        kotlin.test.assertEquals("## Sub {#sub}", lines[1])
        kotlin.test.assertEquals("## Sub {#sub-2}", lines[2])
        kotlin.test.assertEquals("### Another Title {#another-title}", lines[3])
    }

    @Test
    fun renderBlocks_uses_custom_scaffold_when_provided() {
        val cm = TestComposeMark()
        cm.install(PageScaffoldPlugin) {
            scaffold = { props, m ->
                // just invoke content; test checks metadata branch
                props.content(m)
            }
        }

        val meta = PreProcessorMetadata().also { it[PagePathKey] = "/docs/guide/intro.md" }
        val subject = ComposablePipelineContent(
            metadata = meta,
            source = "# Title\n## Sub A",
        ) { }

        val result = cm.renderBlocksPipeline.execute(subject)
        assertEquals(PageScaffoldApplied.Custom, result.metadata[PageScaffoldAppliedKey])
    }
}

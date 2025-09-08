package io.github.arashiyama11.composemark.processor.emitter

import com.squareup.kotlinpoet.FileSpec
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.ParamIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec
import kotlin.test.Test
import kotlin.test.assertContains

class ComposableFunEmitterTest {

    private fun renderFunction(ir: FunctionIR, rendererFactoryFqcn: String = "com.example.MyComposeMark"): String {
        val funSpec = ir.toComposableFun(rendererFactoryFqcn)
        // Wrap into a file to render imports consistently
        val file = FileSpec.builder("com.example", "Tmp")
            .addFunction(funSpec)
            .build()
        return file.toString()
    }

    @Test
    fun `attributes on Composable tag are parsed and emitted to Block_composable`() {
        val ir = FunctionIR(
            name = "WithAttrs",
            parameters = emptyList(),
            source = SourceSpec.FromSource("""
                Before
                <Composable sample lang=\"kotlin\" data-x='1'>content()</Composable>
                After
            """.trimIndent()),
            acceptsModifier = false
        )

        val out = renderFunction(ir)
        println(out)
        // attrs map is emitted with normalized boolean true for bare `sample`
        assertContains(out, "Block.composable(source = \"content()\", attrs = mapOf(\"sample\" to \"true\", \"lang\" to \"kotlin\", \"data-x\" to \"1\"))")
    }

    @Test
    fun `single markdown without modifier param renders with default Modifier`() {
        val ir = FunctionIR(
            name = "About",
            parameters = emptyList(),
            source = SourceSpec.FromSource("  Hello  "),
            acceptsModifier = false
        )

        val out = renderFunction(ir)
        assertContains(out, "override fun About()")
        // Emits RenderBlocks with default Modifier and a remembered blocks list
        assertContains(out, "val blocks = remember {")
        assertContains(out, "listOf(")
        assertContains(out, "Block.markdown(\"Hello\", null)")
        assertContains(out, "renderer.RenderBlocks(blocks, androidx.compose.ui.Modifier, null, fullSource = \"  Hello  \")")
        // remember block is always emitted
        assertContains(out, "val renderer = remember { com.example.MyComposeMark() }")
    }

    @Test
    fun `empty markdown emits no content comment`() {
        val ir = FunctionIR(
            name = "Empty",
            parameters = listOf(ParamIR("modifier", "androidx.compose.ui.Modifier")),
            source = SourceSpec.FromSource("   \n   \n"),
            acceptsModifier = true
        )

        val out = renderFunction(ir)
        assertContains(out, "override fun Empty(modifier: Modifier)")
        assertContains(out, "// No content to render")
    }

    @Test
    fun `mixed sections with unmatched end tag renders blocks sequence`() {
        val ir = FunctionIR(
            name = "Mixed",
            parameters = emptyList(),
            source = SourceSpec.FromSource("A<Composable>B"),
            acceptsModifier = false
        )

        val out = renderFunction(ir)
        // Uses remembered blocks list and RenderBlocks
        assertContains(out, "val blocks = remember {")
        assertContains(out, "listOf(")
        assertContains(out, "Block.markdown(\"A\", null)")
        assertContains(out, "Block.composable(source = \"B\")")
        assertContains(out, "renderer.RenderBlocks(blocks, androidx.compose.ui.Modifier, null, fullSource = \"A<Composable>B\")")
        // Composable section body is emitted as-is
        assertContains(out, "B")
    }

    @Test
    fun `fromPath propagates path to Block and RenderBlocks`() {
        val ir = FunctionIR(
            name = "Doc",
            parameters = listOf(ParamIR("modifier", "androidx.compose.ui.Modifier")),
            source = SourceSpec.FromPath(
                path = "docs/readme.md",
                markdownLiteral = "Hello <Composable>name</Composable>"
            ),
            acceptsModifier = true
        )

        val out = renderFunction(ir)
        // markdown section carries path literal
        assertContains(out, "Block.markdown(\"Hello\", \"docs/readme.md\")")
        // composable section stays path-less (inherits via RenderBlocks)
        assertContains(out, "Block.composable(source = \"name\")")
        // RenderBlocks receives the path literal for inheritance
        assertContains(out, "renderer.RenderBlocks(blocks, modifier, \"docs/readme.md\", fullSource = \"Hello <Composable>name</Composable>\")")
    }
}

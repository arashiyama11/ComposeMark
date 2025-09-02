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
    fun `single markdown without modifier param renders with default Modifier`() {
        val ir = FunctionIR(
            name = "About",
            parameters = emptyList(),
            source = SourceSpec.FromSource("  Hello  "),
            acceptsModifier = false
        )

        val out = renderFunction(ir)
        assertContains(out, "override fun About()")
        // Fallback uses fully-qualified Modifier for single-section path
        assertContains(out, "renderer.Render(androidx.compose.ui.Modifier, null, \"Hello\")")
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
    fun `mixed sections with unmatched end tag renders Column and sections`() {
        val ir = FunctionIR(
            name = "Mixed",
            parameters = emptyList(),
            source = SourceSpec.FromSource("A<Composable>B"),
            acceptsModifier = false
        )

        val out = renderFunction(ir)
        // Column uses provided modifier param name or default fully-qualified one
        assertContains(out, "Column(modifier = androidx.compose.ui.Modifier)")
        // Markdown section inside multi-section uses imported Modifier token
        assertContains(out, "renderer.Render(Modifier, null, \"A\")")
        // Composable section output is emitted as-is
        assertContains(out, "renderer.RenderComposable(")
        assertContains(out, "B")
    }
}

package io.github.arashiyama11.composemark.processor.emitter

import io.github.arashiyama11.composemark.processor.model.ClassIR
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.ParamIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec
import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownEmitterTest {

    @Test
    fun `generates correct file spec with contents map`() {
        val classIR = ClassIR(
            packageName = "com.example",
            interfaceName = "MyMarkdown",
            implName = "MyMarkdownImpl",
            rendererFactoryFqcn = "com.example.MyComposeMark",
            functions = listOf(
                FunctionIR(
                    name = "SimpleGreeting",
                    parameters = listOf(
                        ParamIR("modifier", "androidx.compose.ui.Modifier")
                    ),
                    source = SourceSpec.FromSource("Hello World!"),
                    acceptsModifier = true
                ),
                FunctionIR(
                    name = "GreetingWithName",
                    parameters = listOf(
                        ParamIR("modifier", "androidx.compose.ui.Modifier"),
                        ParamIR("name", "kotlin.String")
                    ),
                    source = SourceSpec.FromSource("Hello, <Composable>name</Composable>!"),
                    acceptsModifier = true
                )
            ),
            contentsPropertyName = "contents"
        )

        val out = classIR.toFileSpec().toString()
        fun has(s: String) = assertTrue(out.contains(s), "Expected to contain: \n$s\n\nIn:\n$out")
        has("package com.example")
        has("object MyMarkdownImpl : MyMarkdown")
        has("override val contents: Map<String, @Composable (modifier: Modifier) -> Unit> = mapOf(")
        has("\"SimpleGreeting\" to { SimpleGreeting(it) }")
        has("@Composable\n  override fun SimpleGreeting(modifier: Modifier)")
        has("val renderer = remember { com.example.MyComposeMark() }")
        has("val blocks = remember {")
        has("Block.markdown(\"Hello World!\", null)")
        has("renderer.RenderBlocks(blocks, modifier, null)")
        has("@Composable\n  override fun GreetingWithName(modifier: Modifier, name: String)")
        has("Block.composable(source = \"name\")")
    }
}

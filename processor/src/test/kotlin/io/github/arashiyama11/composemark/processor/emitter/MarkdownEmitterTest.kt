package io.github.arashiyama11.composemark.processor.emitter

import io.github.arashiyama11.composemark.processor.model.ClassIR
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.ParamIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEmitterTest {

    @Test
    fun `generates correct file spec with contents map`() {
        val classIR = ClassIR(
            packageName = "com.example",
            interfaceName = "MyMarkdown",
            implName = "MyMarkdownImpl",
            rendererFactoryFqcn = "com.example.MyRenderer.Factory",
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

        val fileSpec = classIR.toFileSpec()
        val expected = """
            package com.example

            import androidx.compose.foundation.layout.Column
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember
            import androidx.compose.ui.Modifier
            import kotlin.String
            import kotlin.Unit
            import kotlin.collections.Map

            public object MyMarkdownImpl : MyMarkdown {
              override val contents: Map<String, @Composable (modifier: Modifier) -> Unit> = mapOf(
                "SimpleGreeting" to { SimpleGreeting(it) },
              )

              @Composable
              override fun SimpleGreeting(modifier: Modifier) {
                val renderer = remember { com.example.MyRenderer.Factory() }
                renderer.Render(modifier, null, "Hello World!")
              }

              @Composable
              override fun GreetingWithName(modifier: Modifier, name: String) {
                val renderer = remember { com.example.MyRenderer.Factory() }
                Column(modifier = modifier) {
                  renderer.Render(Modifier, null, "Hello,")
                  renderer.InlineComposableWrapper(modifier = Modifier, source = "name") {
                    name
                  }
                  renderer.Render(Modifier, null, "!")
                }
              }
            }
        """.trimIndent()
        assertEquals(expected, fileSpec.toString().trim())
    }
}

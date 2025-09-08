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
        has("renderer.RenderBlocks(blocks, modifier, null, fullSource = \"Hello World!\")")
        has("@Composable\n  override fun GreetingWithName(modifier: Modifier, name: String)")
        has("Block.composable(source = \"name\")")
    }

    @Test
    fun `deduplicates directory entries that collide with user-declared functions`() {
        val classIR = ClassIR(
            packageName = "com.example",
            interfaceName = "MyMarkdown",
            implName = "MyMarkdownImpl",
            rendererFactoryFqcn = "com.example.MyComposeMark",
            // user-declared abstract function README
            functions = listOf(
                FunctionIR(
                    name = "README",
                    parameters = listOf(ParamIR("modifier", "androidx.compose.ui.Modifier")),
                    source = SourceSpec.FromSource("Hello"),
                    acceptsModifier = true,
                    isOverride = true,
                ),
            ),
            contentsPropertyName = "contents",
            // directory also yields README.md -> functionName README, key README
            directoryEntries = listOf(
                io.github.arashiyama11.composemark.processor.model.DirectoryEntryIR(
                    key = "README",
                    relativePath = "README.md",
                    source = SourceSpec.FromPath("README.md", "Hello from file"),
                    functionName = "README",
                )
            )
        )

        val out = classIR.toFileSpec().toString()
        // Only one README function should be emitted (the override one)
        val occurrences = Regex("""override fun README\(modifier: Modifier\)""").findAll(out).count()
        assertTrue(occurrences == 1, "Expected exactly one README override, got $occurrences in:\n$out")
        // contents map should contain a single entry for README
        val mapOccurrences = Regex("""\"README\" to \{ README\(it\) \}""").findAll(out).count()
        assertTrue(mapOccurrences == 1, "Expected README entry once in contents map, got $mapOccurrences in:\n$out")
    }

    @Test
    fun `generates path literal for FromPath in blocks and RenderBlocks`() {
        val classIR = ClassIR(
            packageName = "com.example",
            interfaceName = "Docs",
            implName = "DocsImpl",
            rendererFactoryFqcn = "com.example.MyComposeMark",
            functions = listOf(
                FunctionIR(
                    name = "Readme",
                    parameters = listOf(
                        ParamIR("modifier", "androidx.compose.ui.Modifier")
                    ),
                    source = SourceSpec.FromPath(
                        path = "docs/x.md",
                        markdownLiteral = "Hello World!"
                    ),
                    acceptsModifier = true
                ),
            ),
            contentsPropertyName = "contents"
        )

        val out = classIR.toFileSpec().toString()
        fun has(s: String) = assertTrue(out.contains(s), "Expected to contain: \n$s\n\nIn:\n$out")
        has("object DocsImpl : Docs")
        has("@Composable\n  override fun Readme(modifier: Modifier)")
        // Path literal appears in markdown and RenderBlocks
        has("Block.markdown(\"Hello World!\", \"docs/x.md\")")
        has("renderer.RenderBlocks(blocks, modifier, \"docs/x.md\", fullSource = \"Hello World!\")")
    }
}

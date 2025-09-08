package io.github.arashiyama11.composemark.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun kotlin(name: String, content: String): SourceFile = SourceFile.kotlin(name, content)

private fun kotlinFromCore(path: String): SourceFile {
    val file = File("../core/src/commonMain/kotlin/io/github/arashiyama11/composemark/core/$path")
    require(file.exists()) { "core file not found: $path" }
    return SourceFile.kotlin(file.name, file.readText())
}

class DirectoryAggregationTest {

    private val composeRuntimeStub = kotlin(
        "ComposeRuntime.kt",
        """
        package androidx.compose.runtime
        @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE)
        @Retention(AnnotationRetention.BINARY)
        annotation class Composable
        inline fun <T> remember(calculation: () -> T): T = calculation()
        interface State<T> { val value: T }
        fun <T> rememberUpdatedState(newValue: T): State<T> = object: State<T> { override val value: T = newValue }
        """.trimIndent()
    )

    private val composeUiStub = kotlin(
        "Modifier.kt",
        """
        package androidx.compose.ui
        object Modifier
        """.trimIndent()
    )

    private val layoutStub = kotlin(
        "Layout.kt",
        """
        package androidx.compose.foundation.layout
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        @Composable fun Column(modifier: Modifier = Modifier, content: @Composable () -> Unit) { content() }
        """.trimIndent()
    )

    private val markdownRendererStub = kotlinFromCore("MarkdownRenderer.kt")
    private val generateContentsStub = kotlinFromCore("annotation/GenerateMarkdownContents.kt")
    private val generateMarkdownStub = kotlinFromCore("annotation/GenerateMarkdownComposable.kt")
    private val generateDirStub = kotlinFromCore("annotation/GenerateMarkdownFromDirectory.kt")

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `aggregates markdown files in directory into contents map`() {
        val testRoot = File("src/test/resources/test_case").absoluteFile
        println("Test root directory: ${testRoot.path}")
        println("files: ${testRoot.walk().joinToString("\n") { it.path }}")
        val src = """
            package test
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromDirectory
            import io.github.arashiyama11.composemark.core.MarkdownRenderer
            import io.github.arashiyama11.composemark.core.ComposeMark
            import io.github.arashiyama11.composemark.core.RenderContext
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            class Renderer: MarkdownRenderer {
                @Composable override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {}
                @Composable override fun RenderComposableBlock(context: RenderContext, modifier: Modifier, content: @Composable () -> Unit) { content() }
            }

            class CM: ComposeMark(Renderer()) { override fun setup() {} }

            @GenerateMarkdownContents(CM::class)
            interface DirDoc {
                @GenerateMarkdownFromDirectory(dir = "docs",includes = ["**/*.md", "**/*.mdcx"], excludes = [])
                val contents: Map<String, @Composable (Modifier) -> Unit>

                companion object : DirDoc by DirDocImpl
            }
        """.trimIndent()

        val compilation = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            sources = listOf(
                composeRuntimeStub,
                composeUiStub,
                layoutStub,
                markdownRendererStub,
                generateContentsStub,
                generateMarkdownStub,
                generateDirStub,
                SourceFile.kotlin("User.kt", src)
            )
            inheritClassPath = true
            symbolProcessorProviders += listOf(MarkdownComposeProcessorProvider() as SymbolProcessorProvider)
            kspProcessorOptions = mutableMapOf("composemark.root.path" to testRoot.path)
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val impl = compilation.kspSourcesDir.resolve("kotlin").walk()
            .firstOrNull { it.name == "DirDocImpl.kt" }
        assertNotNull(impl, "DirDocImpl.kt should be generated")
        val text = impl.readText()
        println("Generated DirDocImpl.kt:\n$text")
        assertTrue(text.contains("override val contents"))
        // only md picked, mdx and txt ignored
        assertTrue(text.contains("\"getting_started\" to"))
        assertTrue(!text.contains("advanced-features"))
        assertTrue(!text.contains("README.txt"))
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `errors when no files matched`() {
        val tempRoot = createEmptyTempRoot()
        val src = """
            package test
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromDirectory
            import io.github.arashiyama11.composemark.core.MarkdownRenderer
            import io.github.arashiyama11.composemark.core.ComposeMark
            import io.github.arashiyama11.composemark.core.RenderContext
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            class Renderer: MarkdownRenderer {
                @Composable override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {}
                @Composable override fun RenderComposableBlock(context: RenderContext, modifier: Modifier, content: @Composable () -> Unit) { content() }
            }

            class CM: ComposeMark(Renderer()) { override fun setup() {} }

            @GenerateMarkdownContents(CM::class)
            interface DirDoc {
                @GenerateMarkdownFromDirectory(dir = "empty")
                val contents: Map<String, @Composable (Modifier) -> Unit>
            }
        """.trimIndent()

        val compilation = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            sources = listOf(
                composeRuntimeStub,
                composeUiStub,
                layoutStub,
                markdownRendererStub,
                generateContentsStub,
                generateMarkdownStub,
                generateDirStub,
                SourceFile.kotlin("User.kt", src)
            )
            inheritClassPath = true
            symbolProcessorProviders += listOf(MarkdownComposeProcessorProvider() as SymbolProcessorProvider)
            kspProcessorOptions = mutableMapOf("composemark.root.path" to tempRoot.path)
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `excludes patterns are applied`() {
        val testRoot = File("src/test/resources/test_case").absoluteFile
        val src = """
            package test
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromDirectory
            import io.github.arashiyama11.composemark.core.MarkdownRenderer
            import io.github.arashiyama11.composemark.core.ComposeMark
            import io.github.arashiyama11.composemark.core.RenderContext
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            class Renderer: MarkdownRenderer {
                @Composable override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {}
                @Composable override fun RenderComposableBlock(context: RenderContext, modifier: Modifier, content: @Composable () -> Unit) { content() }
            }

            class CM: ComposeMark(Renderer()) { override fun setup() {} }

            @GenerateMarkdownContents(CM::class)
            interface DirDoc {
                @GenerateMarkdownFromDirectory(dir = "docs", excludes = ["**/*.md"])
                val contents: Map<String, @Composable (Modifier) -> Unit>
            }
        """.trimIndent()

        val compilation = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            sources = listOf(
                composeRuntimeStub,
                composeUiStub,
                layoutStub,
                markdownRendererStub,
                generateContentsStub,
                generateMarkdownStub,
                generateDirStub,
                SourceFile.kotlin("User.kt", src)
            )
            inheritClassPath = true
            symbolProcessorProviders += listOf(MarkdownComposeProcessorProvider() as SymbolProcessorProvider)
            kspProcessorOptions = mutableMapOf("composemark.root.path" to testRoot.path)
            messageOutputStream = System.out
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    private fun createEmptyTempRoot(): File {
        val root = createTempDir(prefix = "composemark_root_")
        File(root, "empty").mkdirs()
        return root
    }
}

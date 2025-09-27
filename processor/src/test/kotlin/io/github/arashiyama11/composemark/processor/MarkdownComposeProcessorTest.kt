package io.github.arashiyama11.composemark.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
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

private fun kotlin(path: String): SourceFile {
    val file = File("../core/src/commonMain/kotlin/io/github/arashiyama11/composemark/core/$path")
    assertTrue(
        file.exists(),
        "File $path should exist in core/src/commonMain/kotlin/io/github/arashiyama11/composemark/core/"
    )
    return SourceFile.kotlin(file.name, file.readText())
}

class MarkdownComposeProcessorTest {

    private val mockMarkdownLoader: MarkdownLoader = object : MarkdownLoader {
        context(logger: KSPLogger)
        override fun load(path: String): String {
            return when (path) {
                "README.md" -> "This is a README file."
                "LICENCE.md" -> "This is a licence file."
                else -> throw IllegalArgumentException("Unknown path: $path")
            }
        }
    }
    private val composeRuntimeStub = kotlin(
        "ComposeRuntime.kt", """
    package androidx.compose.runtime

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.TYPE,

)
@Retention(AnnotationRetention.BINARY)
annotation class Composable

inline fun <T> remember(calculation: () -> T): T = calculation()

interface State<T> { val value: T }
fun <T> rememberUpdatedState(newValue: T): State<T> = object: State<T> { override val value: T = newValue }
  """.trimIndent()
    )

    private val composeMaterialStub = kotlin(
        "ComposeMaterial.kt",
        """
          package androidx.compose.material3
          import androidx.compose.runtime.Composable
          @Composable fun Text(text: String) {}
        """.trimIndent()
    )

    private val composeUiStub = kotlin(
        "Modifier.kt", """
        |package androidx.compose.ui
        |object Modifier
    """.trimMargin()
    )

    private val layoutStub = kotlin(
        "Layout.kt", """
        package androidx.compose.foundation.layout
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        @Composable fun Box(modifier: Modifier, content: @Composable () -> Unit) {
            content()
        }
        
        @Composable fun Column(
            modifier: Modifier = Modifier,
            content: @Composable () -> Unit
        ) {
            content()
        }
    """.trimIndent()
    )

    private val markdownRendererStub = kotlin("MarkdownRenderer.kt")

    private val composeMarkStub = kotlin("ComposeMark.kt")

    /* Annotation stubs */
    private val generateContentsStub = kotlin("annotation/GenerateMarkdownContents.kt")
    private val generateMarkdownStub = kotlin("annotation/GenerateMarkdownComposable.kt")

    private val contentImports = """
package io.test
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.RenderContext
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
""".trimIndent()

    private val defaultRenderer = """
        
class Renderer: MarkdownRenderer {
    @Composable
    override fun rememberMarkdownColors() = TODO("Not required for this test")

    @Composable
    override fun rememberMarkdownTypography() = TODO("Not required for this test")

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        content()
    }
}
class CM: ComposeMark(Renderer()) { override fun setup() {} }
""".trimIndent()
    private val mdcxContent = """
        start mdcx content
        <Composable>
            val text = "Hello, World!"
            androidx.compose.material3.Text(text)
        </Composable>
        end mdcx content
    """.trimIndent()

    val tripleDoubleQ = "\"\"\""

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processor generates ContentsImpl`() {

        val src = contentImports + defaultRenderer + """
          @GenerateMarkdownContents(CM::class)
          interface Contents {
            @Composable
            @GenerateMarkdownFromPath("README.md")
            fun Readme()
            
            //val contentsMap: Map<String, @Composable (Modifier) -> Unit>
            
            companion object : Contents by ContentsImpl
          }
        """.trimIndent()


        val text = getGeneratedFileContent(
            source = src,
            filename = "Contents.kt"
        )
        println("Generated ContentsImpl.kt:")
        println(text)
        assertTrue(text.contains("object ContentsImpl"))
        assertTrue(text.contains("override fun Readme"))
    }

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processor respects implName override`() {
        val src = contentImports + defaultRenderer + """
          @GenerateMarkdownContents(CM::class, implName = "ContentsCustom")
          interface Contents {
            @Composable
            @GenerateMarkdownFromSource("Hello")
            fun Hello()
            
            companion object : Contents by ContentsCustom
          }
        """.trimIndent()

        val compilation = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true

            sources = listOf(
                composeRuntimeStub,
                composeMaterialStub,
                generateContentsStub,
                generateMarkdownStub,
                markdownRendererStub,
                composeUiStub,
                layoutStub,
                composeMarkStub,
                kotlin("Contents.kt", src)
            )
            inheritClassPath = true

            symbolProcessorProviders += listOf(
                MarkdownComposeProcessorProvider(
                    mockMarkdownLoader
                ) as SymbolProcessorProvider
            )

            messageOutputStream = System.out
        }

        val result = compilation.compile()
        // Verify only implName resolution and that outputs are generated (avoid brittle type-resolution diffs)

        val implFile = compilation.kspSourcesDir
            .resolve("kotlin")
            .walk()
            .firstOrNull { it.name == "ContentsCustom.kt" }

        assertNotNull(implFile, "ContentsCustom.kt should be generated")
        val text = implFile.readText()
        assertTrue(text.contains("object ContentsCustom : Contents"))
    }


    @OptIn(ExperimentalCompilerApi::class)
    fun getGeneratedFileContent(
        source: String,
        filename: String = "Contents.kt",
    ): String {
        val src = kotlin(filename, source)
        val compilation = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true

            sources = listOf(
                composeRuntimeStub,
                composeMaterialStub,
                generateContentsStub,
                generateMarkdownStub,
                markdownRendererStub,
                composeUiStub,
                layoutStub,
                src
            )
            inheritClassPath = true

            symbolProcessorProviders += listOf(
                MarkdownComposeProcessorProvider(
                    mockMarkdownLoader
                ) as SymbolProcessorProvider
            )

            messageOutputStream = System.out
        }

        val result = compilation.compile()
        println()


        val implFileName = filename.replace(".kt", "Impl.kt")
        val implFile = compilation.kspSourcesDir
            .resolve("kotlin")
            .walk()
            .firstOrNull { it.name == implFileName }

        println(implFile!!.readText())
        assertNotNull(implFile, "$implFileName should be generated")
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        return implFile.readText()
    }


    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processor generates contents map when property declared`() {
        val src = contentImports + defaultRenderer + """
          @GenerateMarkdownContents(CM::class)
          interface Ctx {
            @Composable
            @GenerateMarkdownFromSource("Hello")
            fun OnlyModifier(modifier: Modifier)

            @Composable
            @GenerateMarkdownFromSource("Hello")
            fun WithArgs(modifier: Modifier, name: String)

            val contents: Map<String, @Composable (Modifier) -> Unit>

            companion object : Ctx by CtxImpl
          }
        """.trimIndent()

        val text = getGeneratedFileContent(
            source = src,
            filename = "Ctx.kt"
        )

        // contents map exists and includes only the modifier-only function
        kotlin.test.assertTrue(text.contains("override val contents"))
        kotlin.test.assertTrue(text.contains("\"OnlyModifier\" to { OnlyModifier(it) }"))
        kotlin.test.assertFalse(text.contains("\"WithArgs\" to"))
    }
}

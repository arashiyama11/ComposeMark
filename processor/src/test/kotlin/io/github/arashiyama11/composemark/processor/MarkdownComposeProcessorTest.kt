// file: MarkdownComposeProcessorTest.kt
package io.github.arashiyama11.composemark.processor

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

private fun kotlin(name: String, content: String): SourceFile =
    SourceFile.kotlin(name, content)

private fun kotlin(path: String): SourceFile {
    val file = File("../core/src/commonMain/kotlin/io/github/arashiyama11/composemark/core/$path")
    assertTrue(
        file.exists(),
        "File $path should exist in core/src/commonMain/kotlin/io/github/arashiyama11/composemark/core/"
    )
    return SourceFile.kotlin(file.name, file.readText())
}

class MarkdownComposeProcessorTest {
    private val composeRuntimeStub = kotlin(
        "ComposeRuntime.kt", """
    package androidx.compose.runtime

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.TYPE,

)
@Retention(AnnotationRetention.BINARY)   // ← Kotlin の列挙型を使う
annotation class Composable

inline fun <T> remember(calculation: () -> T): T = calculation()
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

    /* ── 注釈スタブ ── */
    private val generateContentsStub = kotlin("annotation/GenerateMarkdownContents.kt")
    private val generateMarkdownStub = kotlin("annotation/GenerateMarkdownComposable.kt")

    private val contentImports = """
package io.test
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
"""

    private val defaultRenderer = """
        
class Renderer: MarkdownRenderer {
    @Composable
    override fun Render(modifier: Modifier, path: String?, source: String) {
        Text(source)
    }
}
"""
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
          @GenerateMarkdownContents(Renderer::class)
          interface Contents {
            @Composable
            @GenerateMarkdownFromPath("README.md")
            fun Readme()
            
            @Composable
            @GenerateMarkdownFromSource(${tripleDoubleQ}${mdcxContent}${tripleDoubleQ})
            fun Licence(modifier: Modifier = Modifier)
            
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
                MarkdownComposeProcessorProvider { "Readme" } as SymbolProcessorProvider
            )

            messageOutputStream = System.out
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val implFileName = filename.replace(".kt", "Impl.kt")
        val implFile = compilation.kspSourcesDir
            .resolve("kotlin")
            .walk()
            .firstOrNull { it.name == implFileName }

        assertNotNull(implFile, "$implFileName should be generated")

        return implFile.readText()
    }
}
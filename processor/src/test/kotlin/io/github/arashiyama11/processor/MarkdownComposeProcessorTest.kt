// file: MarkdownComposeProcessorTest.kt
package io.github.arashiyama11.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import io.github.arashiyama11.composemark.processor.MarkdownComposeProcessorProvider
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

        /** 本物の Composable アノテーション */
        annotation class Composable

        /**
         * テスト用 remember stub
         * 本来は lambda をキャッシュするが、ここでは即実行して値を返すだけ
         */
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

    private val markdownRendererStub = kotlin("MarkdownRenderer.kt")

    /* ── 注釈スタブ ── */
    private val generateContentsStub = kotlin("annotation/GenerateMarkdownContents.kt")
    private val generateMarkdownStub = kotlin("annotation/GenerateMarkdownComposable.kt")

    /* ── テスト対象 ── */
    private val contentsSrc = kotlin(
        "Contents.kt",
        """
          package io.test
          import io.github.arashiyama11.composemark.core.annotation.*
          import androidx.compose.runtime.Composable
          import io.github.arashiyama11.composemark.core.*
          import androidx.compose.material3.Text
          import androidx.compose.ui.Modifier
          
          
          
          class Renderer: MarkdownRenderer{
            @Composable
            override fun Render(modifier: Modifier, path: String?, source: String) {
              Text(source)
            }
          }
          
          @GenerateMarkdownContents(Renderer::class)
          interface Contents {
            @Composable
            @GenerateMarkdownFromPath("README.md")
            fun Readme()
          }
        """.trimIndent()
    )

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `processor generates ContentsImpl`() {

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
                contentsSrc
            )
            inheritClassPath = true

            symbolProcessorProviders += listOf(
                MarkdownComposeProcessorProvider {
                    "# README"
                } as SymbolProcessorProvider
            )

            messageOutputStream = System.out
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val implFile = compilation.kspSourcesDir
            .resolve("kotlin")
            .walk()
            .firstOrNull { it.name == "ContentsImpl.kt" }

        assertNotNull(implFile, "ContentsImpl.kt should be generated")


        val text = implFile.readText()
        println("Generated ContentsImpl.kt:")
        println(text)
        assertTrue(text.contains("object ContentsImpl"))
        assertTrue(text.contains("override fun Readme"))
    }
}
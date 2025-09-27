package io.github.arashiyama11.composemark.processor.analyzer

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import io.github.arashiyama11.composemark.processor.MarkdownLoader
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun kotlin(name: String, content: String): SourceFile =
    SourceFile.kotlin(name, content)

private fun kotlinFromCore(path: String): SourceFile {
    val file = File("../core/src/commonMain/kotlin/io/github/arashiyama11/composemark/core/$path")
    require(file.exists()) { "core file not found: $path" }
    return SourceFile.kotlin(file.name, file.readText())
}

class MarkdownAnalyzerTest {
    private val composeRuntimeStub = kotlin(
        "ComposeRuntime.kt", """
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
        "Modifier.kt", """
        package androidx.compose.ui
        object Modifier
        """.trimIndent()
    )

    private val layoutStub = kotlin(
        "Layout.kt", """
        package androidx.compose.foundation.layout
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        @Composable fun Column(modifier: Modifier = Modifier, content: @Composable () -> Unit) { content() }
        """.trimIndent()
    )

    private val markdownRendererStub = kotlinFromCore("MarkdownRenderer.kt")
    private val generateContentsStub = kotlinFromCore("annotation/GenerateMarkdownContents.kt")
    private val generateMarkdownStub = kotlinFromCore("annotation/GenerateMarkdownComposable.kt")

    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `toClassIR extracts renderer, functions, and contents property`() {
        val source = """
            package test
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
            import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource
            import io.github.arashiyama11.composemark.core.MarkdownRenderer
            import io.github.arashiyama11.composemark.core.ComposeMark
            import io.github.arashiyama11.composemark.core.RenderContext
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            
            class Renderer: MarkdownRenderer {
                @Composable override fun rememberMarkdownColors() = TODO("Not required for this test")
                @Composable override fun rememberMarkdownTypography() = TODO("Not required for this test")
                @Composable override fun RenderComposableBlock(
                    context: RenderContext,
                    modifier: Modifier,
                    content: @Composable () -> Unit
                ) {
                    content()
                }
            }
            class CM: ComposeMark(Renderer()) { override fun setup() {} }
            
            @GenerateMarkdownContents(CM::class)
            interface Doc {
                @Composable @GenerateMarkdownFromPath("doc.md") fun PathCase() 
                @Composable @GenerateMarkdownFromSource("Hello") fun SourceCase(modifier: Modifier)
                val contents: Map<String, @Composable (Modifier) -> Unit>
            }
        """.trimIndent()

        val probeData = runProbe(source)
        // Format: package|interface|impl|rendererFqcn|contentsProp|fn:name:type:accepts;
        val parts = probeData.split("|")
        assertEquals("test", parts[0])
        assertEquals("Doc", parts[1])
        assertEquals("DocImpl", parts[2])
        assertTrue(parts[3].endsWith(".CM"))
        assertEquals("contents", parts[4])

        val functions = parts[5].split(";").filter { it.isNotBlank() }
        // Expect two entries
        assertEquals(2, functions.size)
        val fnMap = functions.associate {
            val cols = it.split(":")
            cols[0] to (cols[1] to cols[2]) // name -> (type, accepts)
        }
        assertEquals("FromPath", fnMap["PathCase"]!!.first)
        assertEquals("false", fnMap["PathCase"]!!.second)
        assertEquals("FromSource", fnMap["SourceCase"]!!.first)
        assertEquals("true", fnMap["SourceCase"]!!.second)
    }

    @OptIn(ExperimentalCompilerApi::class)
    private fun runProbe(userSource: String): String {
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
                SourceFile.kotlin("User.kt", userSource)
            )
            inheritClassPath = true
            symbolProcessorProviders += AnalyzerProbeProcessorProvider { "[loaded]" } as SymbolProcessorProvider
            messageOutputStream = System.out
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val probe = compilation.kspSourcesDir
            .resolve("kotlin")
            .walk()
            .firstOrNull { it.name == "AnalyzerProbe.kt" }
        assertNotNull(probe, "AnalyzerProbe.kt should be generated")
        val text = probe.readText()
        val marker = "const val data: String = \""
        val start = text.indexOf(marker)
        assertTrue(start >= 0, "data const not found in probe file")
        val rest = text.substring(start + marker.length)
        val end = rest.indexOf("\"")
        return rest.substring(0, end)
    }
}

private class AnalyzerProbeProcessorProvider(
    private val loader: MarkdownLoader
) : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        AnalyzerProbeProcessor(environment.codeGenerator, environment.logger, loader)
}

private class AnalyzerProbeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val loader: MarkdownLoader,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(
            "io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents"
        )
        val classDecl =
            symbols.filterIsInstance<KSClassDeclaration>().firstOrNull() ?: return emptyList()
        val ir = classDecl.toClassIR(loader, logger)

        val fnSummary = buildString {
            ir.functions.forEach { f ->
                val type = when (f.source) {
                    is io.github.arashiyama11.composemark.processor.model.SourceSpec.FromPath -> "FromPath"
                    is io.github.arashiyama11.composemark.processor.model.SourceSpec.FromSource -> "FromSource"
                }
                append(f.name).append(":").append(type).append(":").append(f.acceptsModifier)
                    .append(";")
            }
        }
        val content = listOf(
            ir.packageName,
            ir.interfaceName,
            ir.implName,
            ir.rendererFactoryFqcn,
            ir.contentsPropertyName ?: "",
            fnSummary
        ).joinToString("|")

        val file = codeGenerator.createNewFile(
            Dependencies(false, classDecl.containingFile!!),
            packageName = "probe",
            fileName = "AnalyzerProbe",
            extensionName = "kt"
        )
        file.writer().use { w ->
            w.appendLine("package probe")
            w.appendLine("object AnalyzerProbe { const val data: String = \"$content\" }")
        }
        return emptyList()
    }
}

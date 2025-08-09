package io.github.arashiyama11.composemark.processor.emitter

import io.github.arashiyama11.composemark.processor.model.ClassIR
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.ParamIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class MarkdownEmitterImportLiftTest {

    @Test
    fun `imports inside Composable sections are lifted to file imports`() {
        val ir = ClassIR(
            packageName = "com.example",
            interfaceName = "Foo",
            implName = "FooImpl",
            rendererFactoryFqcn = "com.example.Renderer.Factory",
            functions = listOf(
                FunctionIR(
                    name = "Screen",
                    parameters = listOf(ParamIR("modifier", "androidx.compose.ui.Modifier")),
                    source = SourceSpec.FromSource(
                        """
                        <Composable>
                        import androidx.compose.foundation.layout.Row
                        import androidx.compose.material3.Text as T
                        Row { T("hi") }
                        </Composable>
                        """.trimIndent()
                    ),
                    acceptsModifier = true
                )
            ),
            contentsPropertyName = null
        )

        val file = ir.toFileSpec()
        val out = file.toString()

        println(out)
        // File-level imports added
        assertContains(out, "import androidx.compose.foundation.layout.Row")
        assertContains(out, "import androidx.compose.material3.Text as T")

        // Function body should not include raw import lines
        val body = out.substringAfter("override fun Screen").substringBeforeLast("}")
        assertFalse(body.contains("import androidx.compose.foundation.layout.Row"))
        assertFalse(body.contains("import androidx.compose.material3.Text as T"))

        // Composable references remain
        assertContains(out, "Row { T(\"hi\") }")
    }
}


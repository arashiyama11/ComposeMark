package io.github.arashiyama11.composemark.processor.emitter

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.arashiyama11.composemark.processor.model.ClassIR
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.ParamIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ContentsMapEmitterTest {
    @Test
    fun `contents map includes only simple modifier-only functions`() {
        val classIR = ClassIR(
            packageName = "com.example",
            interfaceName = "I",
            implName = "IImpl",
            rendererFactoryFqcn = "com.example.Renderer.Factory",
            functions = listOf(
                FunctionIR(
                    name = "OnlyModifier",
                    parameters = listOf(ParamIR("modifier", "androidx.compose.ui.Modifier")),
                    source = SourceSpec.FromSource("hi"),
                    acceptsModifier = true
                ),
                FunctionIR(
                    name = "WithArgs",
                    parameters = listOf(
                        ParamIR("modifier", "androidx.compose.ui.Modifier"),
                        ParamIR("name", "kotlin.String"),
                    ),
                    source = SourceSpec.FromSource("hi"),
                    acceptsModifier = true
                ),
                FunctionIR(
                    name = "NoModifier",
                    parameters = emptyList(),
                    source = SourceSpec.FromSource("hi"),
                    acceptsModifier = false
                )
            ),
            contentsPropertyName = "contents"
        )

        val prop = classIR.toContentsMapProperty()
        val file = FileSpec.builder("com.example", "Tmp")
            .addType(TypeSpec.objectBuilder("Holder").addProperty(prop).build())
            .build()
        val out = file.toString()

        // includes only the function that matches (modifier only)
        assertContains(out, "\"OnlyModifier\" to { OnlyModifier(it) }")
        assertFalse(out.contains("WithArgs"))
        assertFalse(out.contains("NoModifier"))
    }
}

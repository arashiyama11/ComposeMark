package io.github.arashiyama11.composemark.processor.emitter

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import io.github.arashiyama11.composemark.processor.model.ClassIR

fun ClassIR.toContentsMapProperty(): PropertySpec {
    val composableAnnotation = ClassName("androidx.compose.runtime", "Composable")
    val modifierClassName = ClassName("androidx.compose.ui", "Modifier")
    val modifierParam: ParameterSpec =
        ParameterSpec.builder("modifier", modifierClassName).build()
    val stringClassName = STRING
    val unitClassName = UNIT

    val composableFunType = LambdaTypeName.get(
        receiver = null,
        parameters = listOf(modifierParam),
        returnType = unitClassName as TypeName
    ).copy(annotations = listOf(AnnotationSpec.builder(composableAnnotation).build()))

    val mapType = MAP.parameterizedBy(stringClassName, composableFunType)

    val initializer = CodeBlock.builder()
        .addStatement("mapOf(")
        .indent()

    this.functions
        .filter { it.acceptsModifier && it.parameters.size == 1 }
        .forEach { function ->
            val key = function.name
            initializer.addStatement("%S to { %N(it) },", key, function.name)
        }

    initializer.unindent().addStatement(")")

    return PropertySpec.builder(this.contentsPropertyName!!, mapType)
        .addModifiers(KModifier.OVERRIDE)
        .initializer(initializer.build())
        .build()
}

package io.github.arashiyama11.composemark.processor.emitter

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.arashiyama11.composemark.processor.model.ClassIR

fun ClassIR.toFileSpec(): FileSpec {
    val interfaceClassName = ClassName(this.packageName, this.interfaceName)
    val implClassName = ClassName(this.packageName, this.implName)

    val typeSpec = TypeSpec.objectBuilder(implClassName)
        .addSuperinterface(interfaceClassName)
        .addFunctions(this.functions.map { it.toComposableFun(this.rendererFactoryFqcn) })

    this.contentsPropertyName?.let {
        typeSpec.addProperty(this.toContentsMapProperty())
    }

    return FileSpec.builder(this.packageName, this.implName)
        .addType(typeSpec.build())
        .build()
}

package io.github.arashiyama11.composemark.processor.model

data class ClassIR(
    val packageName: String,
    val interfaceName: String,
    val implName: String,
    val rendererFactoryFqcn: String,
    val functions: List<FunctionIR>,
    val contentsPropertyName: String?,
)

data class FunctionIR(
    val name: String,
    val parameters: List<ParamIR>,
    val source: SourceSpec,
    val acceptsModifier: Boolean
)

data class ParamIR(
    val name: String,
    val typeFqcn: String
)

sealed interface SourceSpec {
    data class FromPath(val path: String, val markdownLiteral: String) : SourceSpec
    data class FromSource(val markdownLiteral: String) : SourceSpec
}

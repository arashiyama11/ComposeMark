package io.github.arashiyama11.composemark.processor.analyzer

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource
import io.github.arashiyama11.composemark.processor.MarkdownLoader
import io.github.arashiyama11.composemark.processor.model.ClassIR
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.ParamIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec

@OptIn(KspExperimental::class)
fun KSClassDeclaration.toClassIR(markdownLoader: MarkdownLoader, logger: KSPLogger): ClassIR {
    val annotationGMC = annotations.first { it.shortName.asString() == "GenerateMarkdownContents" }
    val packageName = this.packageName.asString()
    val interfaceName = this.simpleName.asString()
    val rawImplNameArg = annotationGMC.arguments
        .firstOrNull { it.name?.asString() == "implName" }
        ?.value as? String ?: ""

    val implName = resolveImplName(interfaceName, rawImplNameArg, logger, this)
    val functions = getAllFunctions()
        .filter { it.isAbstract }
        .map { it.toFunctionIR(markdownLoader, logger) }
        .toList()

    val rendererType = annotationGMC.arguments
        .first { it.name?.asString() == "markdownRenderer" }
        .value as com.google.devtools.ksp.symbol.KSType

    val rendererFactoryFqcn = rendererType.declaration.qualifiedName!!.asString()


    val contentsPropertyName = findContentsMapDeclaration(this)

    return ClassIR(
        packageName = packageName,
        interfaceName = interfaceName,
        implName = implName,
        rendererFactoryFqcn = rendererFactoryFqcn,
        functions = functions,
        contentsPropertyName = contentsPropertyName
    )
}

private val KOTLIN_KEYWORDS = setOf(
    "as","break","class","continue","do","else","false","for","fun","if","in","interface","is","null","object","package","return","super","this","throw","true","try","typealias","val","var","when","while"
)

@OptIn(KspExperimental::class)
private fun resolveImplName(
    interfaceName: String,
    implNameArg: String,
    logger: KSPLogger,
    node: KSClassDeclaration,
): String {
    val trimmed = implNameArg.trim()
    if (trimmed.isEmpty()) return "${interfaceName}Impl"

    val regex = Regex("^[A-Z][A-Za-z0-9_]*$")
    if (!regex.matches(trimmed)) {
        logger.error(
            "implName が不正です。形式: ^[A-Z][A-Za-z0-9_]*$、予約語不可。例: MyImpl",
            node
        )
        throw IllegalArgumentException("Invalid implName: $trimmed")
    }
    if (trimmed in KOTLIN_KEYWORDS) {
        logger.error("implName に予約語は使用できません: $trimmed", node)
        throw IllegalArgumentException("Keyword implName: $trimmed")
    }
    return trimmed
}

@OptIn(KspExperimental::class)
private fun KSFunctionDeclaration.toFunctionIR(
    markdownLoader: MarkdownLoader,
    logger: KSPLogger
): FunctionIR {
    val pathAnnotation = getAnnotationsByType(GenerateMarkdownFromPath::class).firstOrNull()
    val sourceAnnotation = getAnnotationsByType(GenerateMarkdownFromSource::class).firstOrNull()

    val sourceSpec = when {
        sourceAnnotation != null -> {
            SourceSpec.FromSource(sourceAnnotation.source)
        }

        pathAnnotation != null -> {
            val path = pathAnnotation.path
            val markdown = with(logger) { markdownLoader.load(path) }
            SourceSpec.FromPath(path, markdown)
        }

        else -> {
            logger.error(
                "No markdown source annotation found for function ${simpleName.asString()}",
                this
            )
            SourceSpec.FromSource("")
        }
    }

    val params = parameters.map {
        ParamIR(
            name = it.name!!.asString(),
            typeFqcn = it.type.resolve().declaration.qualifiedName!!.asString()
        )
    }

    val acceptsModifier = parameters.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName
        ?.asString() == "androidx.compose.ui.Modifier"

    return FunctionIR(
        name = simpleName.asString(),
        parameters = params,
        source = sourceSpec,
        acceptsModifier = acceptsModifier
    )
}

private fun findContentsMapDeclaration(classDeclaration: KSClassDeclaration): String? {
    return classDeclaration.getAllProperties().firstOrNull { prop ->
        val propertyType = prop.type.resolve()
        val isMap = propertyType.declaration.qualifiedName?.asString() == "kotlin.collections.Map"
        if (!isMap) return@firstOrNull false

        val valueType =
            propertyType.arguments.getOrNull(1)?.type?.resolve() ?: return@firstOrNull false
        //TODO Composable check and annotation
        //val isComposable = valueType.declaration.annotations.any { it.shortName.asString() == "Composable" }
        valueType.declaration.qualifiedName?.asString()?.startsWith("kotlin.Function") == true
    }?.simpleName?.asString()
}

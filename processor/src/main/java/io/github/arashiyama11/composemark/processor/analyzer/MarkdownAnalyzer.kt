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
fun KSClassDeclaration.toClassIR(
    markdownLoader: MarkdownLoader,
    logger: KSPLogger,
    rootPath: String? = null,
): ClassIR {
    val annotationGMC = annotations.first { it.shortName.asString() == "GenerateMarkdownContents" }
    val packageName = this.packageName.asString()
    val interfaceName = this.simpleName.asString()
    val args = annotationGMC.arguments
    val rawImplNameArg =
        (args.firstOrNull { it.name?.asString() == "implName" }?.value as? String)
            ?: (args.getOrNull(1)?.value as? String) // positional fallback
            ?: ""

    val implName = resolveImplName(interfaceName, rawImplNameArg, logger, this)
    val baseFunctions = getAllFunctions()
        .filter { it.isAbstract }
        .map { it.toFunctionIR(markdownLoader, logger) }
        .toList()

    val composeMarkType = (
            args.firstOrNull { it.name?.asString() == "composeMark" }?.value
                ?: args.getOrNull(0)?.value
            ) as com.google.devtools.ksp.symbol.KSType

    val rendererFactoryFqcn = composeMarkType.declaration.qualifiedName!!.asString()

    val (contentsPropertyName, dirEntries) = collectContentsMapAndDirEntries(
        classDeclaration = this,
        markdownLoader = markdownLoader,
        logger = logger,
        rootPath = rootPath,
    )

    // Exclude directory-based functions that conflict with user-defined abstract function names
    val baseFunctionNames = baseFunctions.map { it.name }.toSet()
    val filteredDirEntries = dirEntries.filter { it.functionName !in baseFunctionNames }

    // Add directory-based composable function IRs (non-override)
    val dirFunctions: List<FunctionIR> = filteredDirEntries.map { entry ->
        FunctionIR(
            name = entry.functionName,
            parameters = listOf(ParamIR("modifier", "androidx.compose.ui.Modifier")),
            source = entry.source,
            acceptsModifier = true,
            isOverride = false,
        )
    }
    val allFunctions = baseFunctions + dirFunctions

    return ClassIR(
        packageName = packageName,
        interfaceName = interfaceName,
        implName = implName,
        rendererFactoryFqcn = rendererFactoryFqcn,
        functions = allFunctions,
        contentsPropertyName = contentsPropertyName,
        // Keep only non-conflicting entries (prevent undefined refs when generating contentsMap)
        directoryEntries = filteredDirEntries,
    )
}

private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
    "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
    "true", "try", "typealias", "val", "var", "when", "while"
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
            "Invalid implName. Expected format: ^[A-Z][A-Za-z0-9_]*$, reserved words are not allowed. e.g., MyImpl",
            node
        )
        throw IllegalArgumentException("Invalid implName: $trimmed")
    }
    if (trimmed in KOTLIN_KEYWORDS) {
        logger.error("Reserved word not allowed for implName: $trimmed", node)
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
        sourceAnnotation != null -> SourceSpec.FromSource(sourceAnnotation.source)
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
        acceptsModifier = acceptsModifier,
        isOverride = true,
    )
}

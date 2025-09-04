package io.github.arashiyama11.composemark.processor.analyzer

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
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

    // ディレクトリ由来のComposable関数IRを追加（オーバーライドしない）
    val dirFunctions: List<FunctionIR> = dirEntries.map { entry ->
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
        directoryEntries = dirEntries,
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

private fun findContentsMapDeclaration(classDeclaration: KSClassDeclaration): KSPropertyDeclaration? {
    return classDeclaration.getAllProperties().firstOrNull { prop ->
        val propertyType = prop.type.resolve()
        val isMap = propertyType.declaration.qualifiedName?.asString() == "kotlin.collections.Map"
        if (!isMap) return@firstOrNull false
        val valueType =
            propertyType.arguments.getOrNull(1)?.type?.resolve() ?: return@firstOrNull false
        valueType.declaration.qualifiedName?.asString()?.startsWith("kotlin.Function") == true
    }
}
//
//@OptIn(KspExperimental::class)
//private fun collectContentsMapAndDirEntries(
//    classDeclaration: KSClassDeclaration,
//    markdownLoader: MarkdownLoader,
//    logger: KSPLogger,
//    rootPath: String?,
//): Pair<String?, List<DirectoryEntryIR>> {
//    val prop = findContentsMapDeclaration(classDeclaration) ?: return null to emptyList()
//    val propName = prop.simpleName.asString()
//    val dirAnn = prop.getAnnotationsByType(GenerateMarkdownFromDirectory::class).firstOrNull()
//    if (dirAnn == null) return propName to emptyList()
//
//    if (rootPath == null) {
//        logger.error("composemark.root.path が未指定のため、ディレクトリ走査ができません", prop)
//        throw IllegalStateException("composemark.root.path is required")
//    }
//
//    val base = Paths.get(rootPath).normalize().toAbsolutePath()
//    val targetDir = base.resolve(dirAnn.dir).normalize()
//    if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
//        logger.error("指定ディレクトリが存在しません: ${targetDir}", prop)
//        throw IllegalArgumentException("dir not found: ${targetDir}")
//    }
//
//    val paths: List<Path> = Files.walk(targetDir)
//        .filter { Files.isRegularFile(it) }
//        .collect(Collectors.toList())
//
//    val includeMatchers = dirAnn.includes.map { glob ->
//        FileSystems.getDefault().getPathMatcher("glob:${glob}")
//    }
//    val excludeMatchers = dirAnn.excludes.map { glob ->
//        FileSystems.getDefault().getPathMatcher("glob:${glob}")
//    }
//
//    fun matches(matchers: List<java.nio.file.PathMatcher>, p: Path): Boolean {
//        val rel = targetDir.relativize(p)
//        return matchers.any { it.matches(rel) }
//    }
//
//    val selected = paths.filter { p ->
//        (includeMatchers.isEmpty() || matches(includeMatchers, p)) &&
//            (excludeMatchers.isEmpty() || !matches(excludeMatchers, p))
//    }
//
//    if (selected.isEmpty()) {
//        logger.error("対象ディレクトリにマッチするファイルがありません: ${targetDir}", prop)
//        throw IllegalStateException("no files matched in ${targetDir}")
//    }
//
//    val entries = mutableListOf<DirectoryEntryIR>()
//    val seenKeys = mutableSetOf<String>()
//    selected.sortedBy { it.toString() }.forEach { path ->
//        val relFromBase = base.relativize(path).toString().replace(File.separatorChar, '/')
//        val stem = path.fileName.toString().substringBeforeLast('.')
//        val key = stem.replace(Regex("[^A-Za-z0-9]"), "_")
//        if (!seenKeys.add(key)) {
//            logger.error("ディレクトリエントリのキーが衝突しました: ${key}", prop)
//            throw IllegalStateException("duplicate key: ${key}")
//        }
//        val markdown = with(logger) { markdownLoader.load(relFromBase) }
//        val funName = toFunctionNameFromStem(stem)
//        entries += DirectoryEntryIR(
//            key = key,
//            relativePath = relFromBase,
//            source = SourceSpec.FromPath(relFromBase, markdown),
//            functionName = funName,
//        )
//    }
//
//    return propName to entries
//}
//
//private fun toFunctionNameFromStem(stem: String): String {
//    val parts = stem.split(Regex("[^A-Za-z0-9]+"))
//        .filter { it.isNotBlank() }
//    var name = parts.joinToString(separator = "") { p ->
//        p.substring(0, 1).uppercase() + p.substring(1)
//    }
//    if (name.isEmpty()) name = "Doc"
//    if (name.first().isDigit()) name = "Doc${name}"
//    return name
//}
//

package io.github.arashiyama11.composemark.processor.analyzer

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromDirectory
import io.github.arashiyama11.composemark.processor.MarkdownLoader
import io.github.arashiyama11.composemark.processor.model.DirectoryEntryIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.stream.Collectors


@OptIn(KspExperimental::class)
internal fun collectContentsMapAndDirEntries(
    classDeclaration: KSClassDeclaration,
    markdownLoader: MarkdownLoader,
    logger: KSPLogger,
    rootPath: String?,
): Pair<String?, List<DirectoryEntryIR>> {
    val prop = findContentsMapDeclaration(classDeclaration) ?: return null to emptyList()
    val propName = prop.simpleName.asString()

    val dirAnn = prop.getAnnotationsByType(GenerateMarkdownFromDirectory::class).firstOrNull()
    if (dirAnn == null) return propName to emptyList()

    if (rootPath == null) {
        logger.error("composemark.root.path が未指定のため、ディレクトリ走査ができません", prop)
        throw IllegalStateException("composemark.root.path is required")
    }

    val base = Paths.get(rootPath).normalize().toAbsolutePath()
    val targetDir = base.resolve(dirAnn.dir).normalize()
    if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
        logger.error("指定ディレクトリが存在しません: $targetDir", prop)
        throw IllegalArgumentException("dir not found: $targetDir")
    }

    val includeMatchers = dirAnn.includes.flatMap(::expandTopLevel).map(::makeGlobMatcher)
    val excludeMatchers = dirAnn.excludes.flatMap(::expandTopLevel).map(::makeGlobMatcher)

    val paths: List<Path> =
        Files.list(targetDir).filter { Files.isRegularFile(it) }.collect(Collectors.toList())

    val selected = paths.filter { p ->
        println("test : $p")
        (includeMatchers.isEmpty() || matchesAny(targetDir, p, includeMatchers)) &&
                (excludeMatchers.isEmpty() || !matchesAny(targetDir, p, excludeMatchers))
    }

    if (selected.isEmpty()) {
        logger.error("対象ディレクトリにマッチするファイルがありません: $targetDir", prop)
        throw IllegalStateException("no files matched in $targetDir")
    }

    val entries = mutableListOf<DirectoryEntryIR>()
    val seenKeys = mutableSetOf<String>()

    selected.sortedBy { it.toString() }.forEach { path ->
        val rel = base.relativize(path).toString().replace(File.separatorChar, '/')
        val fileName = path.fileName.toString()
        val stem = fileName.substringBeforeLast('.')
        val key = stem.replace(Regex("[^A-Za-z0-9]"), "_")
        if (!seenKeys.add(key)) {
            logger.error("ディレクトリエントリのキーが衝突しました: $key", prop)
            throw IllegalStateException("duplicate key: $key")
        }
        val markdown = with(logger) { markdownLoader.load(rel) }
        entries += DirectoryEntryIR(
            key = key,
            relativePath = rel,
            source = SourceSpec.FromPath(rel, markdown)
        )
    }

    return propName to entries
}


private fun findContentsMapDeclaration(classDeclaration: KSClassDeclaration): KSPropertyDeclaration? {
    return classDeclaration.getAllProperties().firstOrNull { prop ->
        val propertyType = prop.type.resolve()
        val isMap = propertyType.declaration.qualifiedName?.asString() == "kotlin.collections.Map"
        if (!isMap) return@firstOrNull false

        val valueType =
            propertyType.arguments.getOrNull(1)?.type?.resolve() ?: return@firstOrNull false
        //TODO Composable check and annotation
        //val isComposable = valueType.declaration.annotations.any { it.shortName.asString() == "Composable" }
        valueType.declaration.qualifiedName?.asString()?.startsWith("kotlin.Function") == true
    }
}


private fun expandTopLevel(glob: String): List<String> {
    // **/*.ext → {*.ext,**/*.ext} に拡張
    val m = Regex("""^\*\*/\*\.(\w+)$""").matchEntire(glob)
    return if (m != null) {
        val ext = m.groupValues[1]
        listOf("*.${ext}", glob)
    } else {
        listOf(glob)
    }
}

private fun makeGlobMatcher(glob: String): PathMatcher {
    val cleaned = glob.removePrefix("./").removePrefix("/")
    return FileSystems.getDefault().getPathMatcher("glob:$cleaned")
}

private fun matchesAny(targetDir: Path, p: Path, matchers: List<PathMatcher>): Boolean {
    val rel = targetDir.relativize(p).normalize()
    return matchers.any { m ->
        val r1 = m.matches(rel)
        val r2 = m.matches(p.fileName)
        r1 || r2
    }
}
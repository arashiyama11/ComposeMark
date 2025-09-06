package io.github.arashiyama11.composemark.processor.emitter

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.arashiyama11.composemark.processor.model.ClassIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec

fun ClassIR.toFileSpec(): FileSpec {
    val interfaceClassName = ClassName(this.packageName, this.interfaceName)
    val implClassName = ClassName(this.packageName, this.implName)

    // 1) Extract imports from each function's <Composable> sections and remove them from the body
    val collectedImports = LinkedHashSet<ImportDecl>()
    val updatedFunctions = this.functions.map { fn ->
        val (cleaned, imports) = when (val src = fn.source) {
            is SourceSpec.FromSource -> extractImportsFromComposableSections(src.markdownLiteral)
            is SourceSpec.FromPath -> extractImportsFromComposableSections(src.markdownLiteral)
        }
        collectedImports.addAll(imports)

        val newSource = when (val src = fn.source) {
            is SourceSpec.FromSource -> SourceSpec.FromSource(cleaned)
            is SourceSpec.FromPath -> SourceSpec.FromPath(src.path, cleaned)
        }

        fn.copy(source = newSource)
    }

    val typeSpec = TypeSpec.objectBuilder(implClassName)
        .addSuperinterface(interfaceClassName)
        .addFunctions(updatedFunctions.map { it.toComposableFun(this.rendererFactoryFqcn) })

    this.contentsPropertyName?.let {
        typeSpec.addProperty(this.toContentsMapProperty())
    }

    val fileBuilder = FileSpec.builder(this.packageName, this.implName)
        .addType(typeSpec.build())	

    // 2) Add collected imports at the top of the file (LinkedHashSet removes duplicates)
    collectedImports.forEach { imp ->
        if (imp.alias != null) {
            // import with alias
            fileBuilder.addAliasedImport(
                ClassName(imp.packageName, imp.name),
                imp.alias
            )
        } else {
            // normal import (top-level functions/types)
            fileBuilder.addImport(imp.packageName, imp.name)
        }
    }

    return fileBuilder.build()
}

// Import declaration model
private data class ImportDecl(
    val packageName: String,
    val name: String,
    val alias: String? = null,
)

// Collect import lines from <Composable> sections and return the body without them
private fun extractImportsFromComposableSections(markdown: String): Pair<String, List<ImportDecl>> {
    val startTag = "<Composable>"
    val endTag = "</Composable>"
    var cursor = 0
    val sb = StringBuilder()
    val imports = LinkedHashSet<ImportDecl>()

    while (cursor < markdown.length) {
        val startIndex = markdown.indexOf(startTag, cursor)
        if (startIndex == -1) {
            // append the rest as-is
            sb.append(markdown.substring(cursor))
            break
        }
        // write preceding Markdown section
        if (startIndex > cursor) sb.append(markdown.substring(cursor, startIndex))

        val contentStart = startIndex + startTag.length
        val endIndex = markdown.indexOf(endTag, contentStart)
        val compBody = if (endIndex == -1) markdown.substring(contentStart) else markdown.substring(contentStart, endIndex)

        // Extract import lines from the Composable body and keep only non-import lines
        val cleanedComposable = buildString {
            compBody.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                val m = IMPORT_REGEX.matchEntire(line)
                if (m != null) {
                    val fqcn = m.groupValues[1].trim()
                    val alias = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                    // Skip wildcard imports for now (can be added later if needed)
                    if (!fqcn.endsWith(".*")) {
                        val lastDot = fqcn.lastIndexOf('.')
                        if (lastDot > 0 && lastDot < fqcn.length - 1) {
                            val pkg = fqcn.substring(0, lastDot)
                            val name = fqcn.substring(lastDot + 1)
                            imports.add(ImportDecl(pkg, name, alias))
                        }
                    }
                } else {
                    append(rawLine)
                    append('\n')
                }
            }
        }.trimEnd('\n')

        if (cleanedComposable.isNotBlank()) {
            sb.append(startTag)
            sb.append(cleanedComposable)
            sb.append(endTag)
        }

        if (endIndex == -1) break else cursor = endIndex + endTag.length
    }

    return sb.toString() to imports.toList()
}

private val IMPORT_REGEX = Regex("^import\\s+([^\\s]+)(?:\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*))?$")

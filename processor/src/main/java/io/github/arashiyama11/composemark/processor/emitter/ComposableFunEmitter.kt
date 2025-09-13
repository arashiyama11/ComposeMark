package io.github.arashiyama11.composemark.processor.emitter

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.buildCodeBlock
import io.github.arashiyama11.composemark.processor.model.FunctionIR
import io.github.arashiyama11.composemark.processor.model.SourceSpec

fun FunctionIR.toComposableFun(rendererFactoryFqcn: String): FunSpec {
    val composableAnnotation = ClassName("androidx.compose.runtime", "Composable")
    val rememberMember = MemberName("androidx.compose.runtime", "remember")
    val blockClassName = ClassName("io.github.arashiyama11.composemark.core", "Block")

    val funSpec = FunSpec.builder(this.name)
        .apply { if (this@toComposableFun.isOverride) addModifiers(KModifier.OVERRIDE) }
        .addAnnotation(composableAnnotation)
        .returns(Unit::class)

    this.parameters.forEach {
        funSpec.addParameter(it.name, ClassName.bestGuess(it.typeFqcn))
    }

    funSpec.addCode(buildCodeBlock {
        addStatement("val renderer = %M { %L() }", rememberMember, rendererFactoryFqcn)
        val markdownLiteral = when (source) {
            is SourceSpec.FromSource -> source.markdownLiteral
            is SourceSpec.FromPath -> source.markdownLiteral
        }
        val pathLiteral = when (source) {
            is SourceSpec.FromPath -> "\"${source.path}\""
            else -> "null"
        }

        val modifierParamName =
            if (acceptsModifier) parameters.first().name else "androidx.compose.ui.Modifier"

        emitBlocksAndRender(
            this,
            markdownLiteral,
            modifierParamName,
            pathLiteral,
            blockClassName,
            rememberMember
        )
    })

    return funSpec.build()
}

private fun emitBlocksAndRender(
    builder: CodeBlock.Builder,
    markdown: String,
    modifierParamName: String,
    pathLiteral: String,
    blockClassName: ClassName,
    rememberMember: MemberName,
) {
    val sections = markdownToSections(markdown)
    if (sections.isEmpty()) {
        builder.addStatement("// No content to render")
        return
    }

    builder.add("val blocks = %M {\n", rememberMember)
    builder.add("  listOf(\n")
    sections.forEach { section ->
        when (section) {
            is MarkdownSection.Markdown -> {
                builder.addStatement(
                    "  %T.markdown(%S, %L),",
                    blockClassName,
                    section.content.trim(),
                    pathLiteral,
                )
            }

            is MarkdownSection.Composable -> {
                builder.add(
                    "  %T.composable(source = %S",
                    blockClassName,
                    section.content.trim()
                )
                if (section.attrs.isNotEmpty()) {
                    val pairs = section.attrs.entries.joinToString(", ") { (k, v) -> "%S to %S" }
                    val args = mutableListOf<Any>()
                    section.attrs.forEach { (k, v) ->
                        args += k; args += (v ?: "")
                    }
                    builder.add(
                        ", attrs = mapOf($pairs)",
                        *args.toTypedArray()
                    )
                }
                builder.add(") {\n")
                builder.addStatement("  %L", section.content.trim())
                builder.add("},\n")
            }
        }
    }
    builder.add("  )\n")
    builder.add("}\n")

    builder.addStatement(
        "renderer.RenderBlocks(blocks, %L, %L, fullSource = %S)",
        modifierParamName,
        pathLiteral,
        markdown,
    )
}

private sealed interface MarkdownSection {
    val content: String

    data class Markdown(override val content: String) : MarkdownSection
    data class Composable(override val content: String, val attrs: Map<String, String?>) : MarkdownSection
}

private fun markdownToSections(markdown: String): List<MarkdownSection> {
    val sections = mutableListOf<MarkdownSection>()
    var cursor = 0
    val startPrefix = "<Composable"
    val endTag = "</Composable>"

    while (cursor < markdown.length) {
        val startIndex = markdown.indexOf(startPrefix, cursor)
        if (startIndex == -1) {
            if (cursor < markdown.length) {
                sections.add(MarkdownSection.Markdown(markdown.substring(cursor)))
            }
            break
        }
        if (startIndex > cursor) {
            sections.add(MarkdownSection.Markdown(markdown.substring(cursor, startIndex)))
        }
        val openEnd = markdown.indexOf('>', startIndex)
        if (openEnd == -1) {
            // malformed opening; treat rest as markdown
            sections.add(MarkdownSection.Markdown(markdown.substring(startIndex)))
            break
        }
        val openTag = markdown.substring(startIndex, openEnd + 1)
        val attrsText = openTag.removePrefix("<Composable").removeSuffix(">")
        val attrs = parseAttributes(attrsText)
        val contentStart = openEnd + 1
        val endIndex = markdown.indexOf(endTag, contentStart)
        if (endIndex == -1) {
            sections.add(MarkdownSection.Composable(markdown.substring(contentStart), attrs))
            break
        } else {
            val compContent = markdown.substring(contentStart, endIndex)
            sections.add(MarkdownSection.Composable(compContent, attrs))
            cursor = endIndex + endTag.length
        }
    }
    return sections.filter { it.content.isNotBlank() }
}

private fun parseAttributes(raw: String): Map<String, String?> {
    val text = raw.trim()
    if (text.isEmpty()) return emptyMap()
    val attrs = linkedMapOf<String, String?>()
    var i = 0
    fun skipWs() { while (i < text.length && text[i].isWhitespace()) i++ }
    fun readIdent(): String {
        val start = i
        if (i >= text.length) return ""
        if (!text[i].isLetter() && text[i] != '_' ) return ""
        i++
        while (i < text.length) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '_' || c == '-') i++ else break
        }
        return text.substring(start, i)
    }
    fun readValue(): String {
        if (i >= text.length) return ""
        return when (text[i]) {
            '"' -> {
                i++
                val start = i
                while (i < text.length && text[i] != '"') i++
                val v = text.substring(start, i)
                if (i < text.length && text[i] == '"') i++
                v
            }
            '\'' -> {
                i++
                val start = i
                while (i < text.length && text[i] != '\'') i++
                val v = text.substring(start, i)
                if (i < text.length && text[i] == '\'') i++
                v
            }
            else -> {
                val start = i
                while (i < text.length && !text[i].isWhitespace() && text[i] != '>') i++
                text.substring(start, i)
            }
        }
    }

    while (i < text.length) {
        skipWs()
        val key = readIdent()
        if (key.isEmpty()) break
        skipWs()
        var value = if (i < text.length && text[i] == '=') {
            i++
            skipWs()
            readValue()
        } else {
            "true"
        }
        // Normalize escaped quotes like \"value\" or \'value\'
        if (value.length >= 4 && value.startsWith("\\\"") && value.endsWith("\\\"")) {
            value = value.substring(2, value.length - 2)
        } else if (value.length >= 4 && value.startsWith("\\'") && value.endsWith("\\'")) {
            value = value.substring(2, value.length - 2)
        }
        attrs[key] = value
    }
    return attrs
}

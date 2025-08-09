package io.github.arashiyama11.composemark.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.github.arashiyama11.composemark.processor.util.literalEscaped

internal fun generateCMFunction(
    fn: KSFunctionDeclaration,
    markdownLoader: MarkdownLoader,
    rendererFactoryFqcn: String
): Sequence<String> = sequence {
    val fnName = fn.simpleName.asString()

    val params = fn.parameters.joinToString(", ") {
        val name = it.name?.asString() ?: "it"
        val type = it.type.resolve().declaration.qualifiedName?.asString() ?: "Any"
        "$name: $type"
    }

    val modifierParamName =
        fn.parameters.firstOrNull { it.type.resolve().declaration.qualifiedName?.asString() == "androidx.compose.ui.Modifier" }
            ?.name?.asString() ?: "Modifier"

    yield("    @Composable")
    yield("    override fun $fnName($params) {")
    yield("        val renderer = remember { $rendererFactoryFqcn() }")
    yieldAll(generateRenderCall(fn, markdownLoader, modifierParamName))
    yield("    }")
    yield("")
}

internal fun generateRenderCall(
    fn: KSFunctionDeclaration,
    markdownLoader: MarkdownLoader,
    modifierParamName: String = "Modifier"
): Sequence<String> = sequence {

    // Determine markdown text literal
    val pathAnno = fn.annotations
        .firstOrNull { it.shortName.asString() == "GenerateMarkdownFromPath" }
    val sourceAnno = fn.annotations
        .firstOrNull { it.shortName.asString() == "GenerateMarkdownFromSource" }

    var path: String? = null
    val textLiteral = when {
        sourceAnno != null -> {
            sourceAnno.arguments.first().value as String
        }

        pathAnno != null -> {
            path = pathAnno.arguments.first().value as String
            markdownLoader.load(path)
        }

        else -> ""
    }


    val pathLiteral = path?.let { "\"$it\"" } ?: "null"

    val sections = markdownToSections(textLiteral)

    if (sections.isEmpty()) {
        yield("        // No content to render")
        return@sequence
    }
    if (sections.size == 1) {
        when (val section = sections[0]) {
            is MarkdownSection.Markdown -> yield(
                "        renderer.Render($modifierParamName, $pathLiteral,  ${
                    section.content.literalEscaped().trim()
                })"
            )

            is MarkdownSection.Composable -> yield("      ${section.content}")
        }
    } else {
        yield("        androidx.compose.foundation.layout.Column(modifier = $modifierParamName) {")
        sections.forEach { section ->
            when (section) {
                is MarkdownSection.Markdown -> yield(
                    "        renderer.Render(Modifier, $pathLiteral,  ${
                        section.content.literalEscaped().trim()
                    })"
                )

                is MarkdownSection.Composable -> yield("      ${section.content}")
            }
        }

        yield("        }")
    }


}

internal sealed interface MarkdownSection {
    val content: String

    data class Markdown(override val content: String) : MarkdownSection
    data class Composable(override val content: String) : MarkdownSection
}


private const val startTag = "<Composable>"
private const val endTag = "</Composable>"

internal fun markdownToSections(markdown: String): List<MarkdownSection> {
    val sections = mutableListOf<MarkdownSection>()
    var cursor = 0


    while (cursor < markdown.length) {
        val startIndex = markdown.indexOf(startTag, cursor)
        if (startIndex == -1) {
            sections += MarkdownSection.Markdown(markdown.substring(cursor))
            break
        }
        if (startIndex > cursor) {
            sections += MarkdownSection.Markdown(markdown.substring(cursor, startIndex))
        }
        val contentStart = startIndex + startTag.length
        val endIndex = markdown.indexOf(endTag, contentStart)
        if (endIndex == -1) {
            sections += MarkdownSection.Composable(markdown.substring(contentStart))
            break
        } else {
            val compContent = markdown.substring(contentStart, endIndex)
            sections += MarkdownSection.Composable(compContent)
            cursor = endIndex + endTag.length
        }
    }

    return sections
}
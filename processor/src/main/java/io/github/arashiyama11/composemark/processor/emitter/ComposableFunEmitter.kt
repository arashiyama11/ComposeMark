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
                    "  %T.composable(source = %S) {\n",
                    blockClassName,
                    section.content.trim()
                )
                builder.addStatement("  %L", section.content.trim())
                builder.add("},\n")
            }
        }
    }
    builder.add("  )\n")
    builder.add("}\n")

    builder.addStatement("renderer.RenderBlocks(blocks, %L, %L)", modifierParamName, pathLiteral)
}

private sealed interface MarkdownSection {
    val content: String

    data class Markdown(override val content: String) : MarkdownSection
    data class Composable(override val content: String) : MarkdownSection
}

private fun markdownToSections(markdown: String): List<MarkdownSection> {
    val sections = mutableListOf<MarkdownSection>()
    var cursor = 0
    val startTag = "<Composable>"
    val endTag = "</Composable>"

    while (cursor < markdown.length) {
        val startIndex = markdown.indexOf(startTag, cursor)
        if (startIndex == -1) {
            if (cursor < markdown.length) {
                sections.add(MarkdownSection.Markdown(markdown.substring(cursor)))
            }
            break
        }
        if (startIndex > cursor) {
            sections.add(MarkdownSection.Markdown(markdown.substring(cursor, startIndex)))
        }
        val contentStart = startIndex + startTag.length
        val endIndex = markdown.indexOf(endTag, contentStart)
        if (endIndex == -1) {
            sections.add(MarkdownSection.Composable(markdown.substring(contentStart)))
            break
        } else {
            val compContent = markdown.substring(contentStart, endIndex)
            sections.add(MarkdownSection.Composable(compContent))
            cursor = endIndex + endTag.length
        }
    }
    return sections.filter { it.content.isNotBlank() }
}

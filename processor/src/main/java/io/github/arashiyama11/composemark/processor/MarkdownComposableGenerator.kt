package io.github.arashiyama11.composemark.processor

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.github.arashiyama11.composemark.processor.util.literalEscaped

internal fun generateCMFunction(
    fn: KSFunctionDeclaration,
    markdownLoader: MarkdownLoader,
    rendererFactoryFqcn: String
): Sequence<String> = sequence {
    val fnName = fn.simpleName.asString()

    yield("    @Composable")
    yield("    override fun $fnName() {")
    yield("        val renderer = remember { $rendererFactoryFqcn() }")
    yieldAll(generateRenderCall(fn, markdownLoader))
    yield("    }")
    yield("")
}

internal fun generateRenderCall(
    fn: KSFunctionDeclaration,
    markdownLoader: MarkdownLoader,
): Sequence<String> = sequence {

    // Determine markdown text literal
    val pathAnno = fn.annotations
        .firstOrNull { it.shortName.asString() == "GenerateMarkdownFromPath" }
    val sourceAnno = fn.annotations
        .firstOrNull { it.shortName.asString() == "GenerateMarkdownFromSource" }

    var path: String? = null
    val textLiteral = when {
        sourceAnno != null -> {
            val src = sourceAnno.arguments.first().value as String
            src.literalEscaped()
        }

        pathAnno != null -> {
            path = pathAnno.arguments.first().value as String
            markdownLoader.load(path).literalEscaped()
        }

        else -> "\"\""
    }

    val pathLiteral = path?.let { "\"$it\"" } ?: "null"


    yield("        renderer.Render(Modifier, $pathLiteral,  $textLiteral)")
}


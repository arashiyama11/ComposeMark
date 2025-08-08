package io.github.arashiyama11.composemark.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import java.io.File
import java.io.OutputStreamWriter

fun interface MarkdownLoader {
    fun load(path: String): String
}

class DefaultMarkdownLoader : MarkdownLoader {
    override fun load(path: String): String = File(path).readText()
}

class MarkdownComposeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val markdownLoader: MarkdownLoader
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("Starting MarkdownComposeProcessor", null)
        // @GenerateMarkdownContents が付いたクラスを探す
        val symbols = resolver
            .getSymbolsWithAnnotation(GenerateMarkdownContents::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        logger.info(
            "Found ${symbols.count()} classes with @GenerateMarkdownContents",
            null
        )
        symbols.forEach { ksClass ->
            logger.info(
                "Processing class: ${ksClass.qualifiedName?.asString()}",
                ksClass
            )
            generateContentsImpl(ksClass)
        }
        return emptyList()
    }

    private fun generateContentsImpl(classDecl: KSClassDeclaration) {
        val pkg = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()
        val implName = "${className}Impl"

        // Create file
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false, classDecl.containingFile!!),
            packageName = pkg,
            fileName = implName,
            extensionName = "kt"
        )

        // Read the factory class from @GenerateMarkdownContents
        val contentsAnno = classDecl.annotations
            .first { it.shortName.asString() == "GenerateMarkdownContents" }
        val rendererFactoryType = contentsAnno.arguments
            .first { it.name?.asString() == "markdownRenderer" }
            .value as KSType
        val rendererFactoryFqcn = rendererFactoryType.declaration.qualifiedName!!.asString()

        OutputStreamWriter(file, "UTF-8").use { w ->

            w.appendLine("package $pkg")
            w.appendLine()
            w.appendLine("import androidx.compose.runtime.Composable")
            w.appendLine("import androidx.compose.ui.Modifier")
            w.appendLine("import androidx.compose.runtime.remember")
            w.appendLine("import $rendererFactoryFqcn")
            w.appendLine()
            w.appendLine("object $implName : $className {")
            w.appendLine()

            // For each function annotated with either path or source
            val declaredFns = classDecl.getAllFunctions()
                .filter { fn ->
                    fn.annotations.any {
                        val name = it.shortName.asString()
                        name == "GenerateMarkdownFromPath" || name == "GenerateMarkdownFromSource"
                    }
                }

            declaredFns.forEach { fn ->
                generateCMFunction(fn, markdownLoader, rendererFactoryFqcn).forEach {
                    w.appendLine(it)
                }
            }

            val contentsProperty = findContentsMapDeclaration(classDecl)

            generateContentsMap(
                isOverride = contentsProperty != null,
                propertyName = contentsProperty?.simpleName?.asString() ?: "contents",
                declaredFns = declaredFns
            ).forEach { line ->
                w.appendLine(line)
            }

            w.appendLine("}")
        }
    }

    override fun finish() {}
}


@AutoService(SymbolProcessorProvider::class)
class MarkdownComposeProcessorProvider(
    private val markdownLoader: MarkdownLoader = DefaultMarkdownLoader()
) : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {

        return MarkdownComposeProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            markdownLoader = markdownLoader
        )
    }
}

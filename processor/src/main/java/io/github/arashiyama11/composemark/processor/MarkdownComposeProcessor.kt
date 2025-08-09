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
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ksp.writeTo
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.processor.analyzer.toClassIR
import io.github.arashiyama11.composemark.processor.emitter.toFileSpec
import java.io.File


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
        val symbols = resolver.getSymbolsWithAnnotation(GenerateMarkdownContents::class.java.name)
        val (validSymbols, invalidSymbols) = symbols.partition { it.validate() }

        validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                try {
                    val classIR = classDeclaration.toClassIR(markdownLoader, logger)
                    val fileSpec = classIR.toFileSpec()
                    fileSpec.writeTo(
                        codeGenerator = codeGenerator,
                        dependencies = Dependencies(
                            aggregating = true,
                            sources = arrayOf(classDeclaration.containingFile!!)
                        )
                    )
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process ${classDeclaration.qualifiedName?.asString()}: ${e.stackTraceToString()}",
                        classDeclaration
                    )
                }
            }

        return invalidSymbols
    }
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
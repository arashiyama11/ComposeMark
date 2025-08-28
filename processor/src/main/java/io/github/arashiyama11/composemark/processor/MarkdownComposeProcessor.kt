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
import java.io.FileNotFoundException


fun interface MarkdownLoader {
    context(logger: KSPLogger)
    fun load(path: String): String
}

class DefaultMarkdownLoader(
    var rootPath: String? = null,
) : MarkdownLoader {
    context(logger: KSPLogger)
    override fun load(path: String): String {
        val file = if (rootPath != null) {
            File(rootPath, path)
        } else {
            File(path)
        }

        if (!file.exists()) {
            logger.error("Markdown file not found: ${file.absolutePath}")
            throw FileNotFoundException()
        }
        return file.readText()
    }
}

class MarkdownComposeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val markdownLoader: MarkdownLoader,
    private val rootPath: String?
) : SymbolProcessor {

    private val generatedFqcns = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenerateMarkdownContents::class.java.name)
        val (validSymbols, invalidSymbols) = symbols.partition { it.validate() }

        validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                try {
                    val classIR = classDeclaration.toClassIR(markdownLoader, logger, rootPath)
                    val fqcn = classIR.packageName + "." + classIR.implName

                    // 同一ラウンドでの重複を検出
                    if (!generatedFqcns.add(fqcn)) {
                        logger.error("生成クラス名が同一ラウンド内で衝突しています: $fqcn", classDeclaration)
                        return@forEach
                    }

                    // 既存ソース/過去生成物との衝突を事前検出
                    val existing = resolver.getClassDeclarationByName(resolver.getKSNameFromString(fqcn))
                    if (existing != null) {
                        logger.error("生成クラス名が既存と衝突しています: $fqcn。別の implName を指定してください", classDeclaration)
                        return@forEach
                    }
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
    private val markdownLoader: MarkdownLoader? = null
) : SymbolProcessorProvider {

    // Default constructor for KSP to instantiate
    constructor() : this(null)

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val rootPath = environment.options["composemark.root.path"]
        val loader = markdownLoader ?: DefaultMarkdownLoader(rootPath)

        return MarkdownComposeProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            markdownLoader = loader,
            rootPath = rootPath
        )
    }
}

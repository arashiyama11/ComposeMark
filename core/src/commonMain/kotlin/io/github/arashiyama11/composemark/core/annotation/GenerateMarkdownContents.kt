package io.github.arashiyama11.composemark.core.annotation

import io.github.arashiyama11.composemark.core.MarkdownRenderer
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
public annotation class GenerateMarkdownContents(
    val markdownRenderer: KClass<out MarkdownRenderer>,
    val implName: String = "",
)

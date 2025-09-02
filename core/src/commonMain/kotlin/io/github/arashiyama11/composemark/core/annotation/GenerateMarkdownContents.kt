package io.github.arashiyama11.composemark.core.annotation

import io.github.arashiyama11.composemark.core.ComposeMark
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
public annotation class GenerateMarkdownContents(
    val composeMark: KClass<out ComposeMark>,
    val implName: String = "",
)

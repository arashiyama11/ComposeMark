package io.github.arashiyama11.composemark.core.annotation

@Target(AnnotationTarget.FUNCTION)
public annotation class GenerateMarkdownFromPath(val path: String)

@Target(AnnotationTarget.FUNCTION)
public annotation class GenerateMarkdownFromSource(val source: String)

package io.github.arashiyama11.composemark.core.annotation

@Target(AnnotationTarget.PROPERTY)
public annotation class GenerateMarkdownFromDirectory(
    val dir: String,
    val includes: Array<String>,
    val excludes: Array<String>,
)

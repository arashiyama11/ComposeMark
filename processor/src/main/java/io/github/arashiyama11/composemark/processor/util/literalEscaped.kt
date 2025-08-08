package io.github.arashiyama11.composemark.processor.util


internal fun String.literalEscaped(): String =
    "\"${
        this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }\""
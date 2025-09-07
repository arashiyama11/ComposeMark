package io.github.arashiyama11.composemark.plugin

internal fun slugify(text: String): String = buildString {
    var prevHyphen = false
    text.lowercase().forEach { ch ->
        when {
            ch.isLetterOrDigit() -> {
                append(ch)
                prevHyphen = false
            }
            ch.isWhitespace() || ch == '-' || ch == '_' -> {
                if (!prevHyphen) append('-')
                prevHyphen = true
            }
            else -> {
                // skip punctuation
            }
        }
    }
}.trim('-')


package io.github.arashiyama11.composemark.core

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public interface MarkdownRenderer {

    @Composable
    public fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier)

    @Composable
    public fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit,
    )

    @Composable
    public fun BlockContainer(
        modifier: Modifier,
        contents: List<BlockEntry>
    ) {
        Column(modifier) {
            contents.forEach { it.content(Modifier) }
        }
    }
}

public data class BlockEntry(
    val context: RenderContext,
    val content: @Composable (Modifier) -> Unit,
)

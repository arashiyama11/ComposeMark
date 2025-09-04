package io.github.arashiyama11.composemark.core

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public interface MarkdownRenderer {

    @Composable
    public fun RenderMarkdownBlock(modifier: Modifier, path: String?, source: String)

    @Composable
    public fun RenderComposableBlock(
        modifier: Modifier,
        source: String,
        content: @Composable () -> Unit,
    )

    @Composable
    public fun BlockContainer(
        modifier: Modifier,
        contents: List<@Composable () -> Unit>
    ) {
        Column(modifier) {
            contents.forEach { it() }
        }
    }
}

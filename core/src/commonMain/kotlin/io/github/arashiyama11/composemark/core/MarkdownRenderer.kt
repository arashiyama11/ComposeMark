package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public interface MarkdownRenderer {

    @Composable
    public fun Render(modifier: Modifier, path: String?, source: String)

    @Composable
    public fun RenderComposable(
        modifier: Modifier,
        source: String,
        content: @Composable () -> Unit,
    )
}

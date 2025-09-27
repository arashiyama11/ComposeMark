package io.github.arashiyama11.composemark.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


public interface BlockItem {
    public val source: String

    @Composable
    public fun Render(composeMark: ComposeMark, context: RenderContext, modifier: Modifier)

    public companion object {
        public operator fun invoke(
            source: String,
            render: @Composable (composeMark: ComposeMark, context: RenderContext, modifier: Modifier) -> Unit
        ): BlockItem = object : BlockItem {
            override val source: String = source

            @Composable
            override fun Render(
                composeMark: ComposeMark,
                context: RenderContext,
                modifier: Modifier
            ) {
                render(composeMark, context, modifier)
            }
        }
    }
}

internal interface BlockItemMeta {
    val attrs: Map<String, String?>
    val explicitPath: String?
}

public object Block {
    public fun markdown(source: String, path: String?): BlockItem =
        object : BlockItem, BlockItemMeta {
            override val source: String = source
            override val attrs: Map<String, String?> = emptyMap()
            override val explicitPath: String? = path

            @Composable
            override fun Render(
                composeMark: ComposeMark,
                context: RenderContext,
                modifier: Modifier
            ) {
                composeMark.RenderMarkdownBlock(context.copy(path = path), modifier)
            }
        }

    public fun composable(
        source: String,
        path: String? = null,
        attrs: Map<String, String?> = emptyMap(),
        content: @Composable () -> Unit,
    ): BlockItem = object : BlockItem, BlockItemMeta {
        override val source: String = source
        override val attrs: Map<String, String?> = attrs
        override val explicitPath: String? = path

        @Composable
        override fun Render(
            composeMark: ComposeMark,
            context: RenderContext,
            modifier: Modifier
        ) {
            composeMark.RenderComposableBlock(
                context.copy(path = path, attrs = attrs),
                modifier
            ) { content() }
        }
    }
}

internal fun resolvePath(explicit: String?, inherited: String?): String? = explicit ?: inherited

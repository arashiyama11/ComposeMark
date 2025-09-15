package io.github.arashiyama11.composemark.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import io.github.arashiyama11.composemark.core.BlockEntry
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.RenderContext
import io.github.arashiyama11.composemark.plugin.LocalAnchorModifier
import org.intellij.markdown.MarkdownTokenTypes

class MarkdownRendererImpl : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {
        val color = rememberMarkdownColors()
        val typography = rememberMarkdownTypography()
        val am = LocalAnchorModifier.current
        val mcs = markdownComponents(
            heading1 = {
                val modifier = am(context.source.substring(it.node.startOffset, it.node.endOffset))

                MarkdownText(
                    modifier = modifier
                        .semantics {
                            heading()
                        },
                    content = it.content,
                    node = it.node,
                    style = it.typography.h1,
                    contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                )
            },

            heading2 = {
                val modifier = am(context.source.substring(it.node.startOffset, it.node.endOffset))

                MarkdownText(
                    modifier = modifier
                        .semantics {
                            heading()
                        },
                    content = it.content,
                    node = it.node,
                    style = it.typography.h2,
                    contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                )
            },
            heading3 = {
                val modifier = am(context.source.substring(it.node.startOffset, it.node.endOffset))

                MarkdownText(
                    modifier = modifier
                        .semantics {
                            heading()
                        },
                    content = it.content,
                    node = it.node,
                    style = it.typography.h3,
                    contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                )
            },

            heading4 = {
                val modifier = am(context.source.substring(it.node.startOffset, it.node.endOffset))

                MarkdownText(
                    modifier = modifier
                        .semantics {
                            heading()
                        },
                    content = it.content,
                    node = it.node,
                    style = it.typography.h4,
                    contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                )
            },

            heading5 = {
                val modifier = am(context.source.substring(it.node.startOffset, it.node.endOffset))

                MarkdownText(
                    modifier = modifier
                        .semantics {
                            heading()
                        },
                    content = it.content,
                    node = it.node,
                    style = it.typography.h5,
                    contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                )
            },

            heading6 = {
                val modifier = am(context.source.substring(it.node.startOffset, it.node.endOffset))

                MarkdownText(
                    modifier = modifier
                        .semantics {
                            heading()
                        }
                        .background(Color.Yellow),
                    content = it.content,
                    node = it.node,
                    style = it.typography.h6,
                    contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                )
            },
        )
        Markdown(
            content = context.source,
            modifier = modifier,
            colors = color,
            typography = typography,
            components = mcs
        )
    }

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable (() -> Unit)
    ) {
        content()
    }

    @Composable
    override fun BlockContainer(modifier: Modifier, contents: List<BlockEntry>) {
        Column(modifier) {
            contents.forEach {
                it.content(Modifier)
            }
        }
    }
}


@Composable
fun rememberMarkdownColors(): MarkdownColors {
    val colorScheme = MaterialTheme.colorScheme

    val colors = remember {
        DefaultMarkdownColors(
            text = colorScheme.onBackground,
            codeBackground = colorScheme.surfaceVariant,
            inlineCodeBackground = colorScheme.surfaceVariant,
            dividerColor = colorScheme.outline,
            tableBackground = colorScheme.surface
        )
    }

    return colors
}

@Composable
fun rememberMarkdownTypography(): MarkdownTypography {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val markdownTypography = remember {
        DefaultMarkdownTypography(
            h1 = typography.headlineLarge,
            h2 = typography.headlineMedium,
            h3 = typography.headlineSmall,
            h4 = typography.titleLarge,
            h5 = typography.titleMedium,
            h6 = typography.titleSmall,
            text = typography.bodyLarge,
            code = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            inlineCode = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            quote = typography.bodyLarge.copy(color = colorScheme.secondary),
            paragraph = typography.bodyLarge,
            ordered = typography.bodyLarge,
            bullet = typography.bodyLarge,
            list = typography.bodyLarge,
            textLink = TextLinkStyles(),
            table = typography.bodyMedium
        )
    }

    return markdownTypography
}

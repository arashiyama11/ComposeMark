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
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import io.github.arashiyama11.composemark.core.BlockEntry
import io.github.arashiyama11.composemark.core.MarkdownProperty
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.RenderContext
import org.intellij.markdown.MarkdownTokenTypes


class MarkdownRendererImpl : MarkdownRenderer {
    @Composable
    override fun rememberMarkdownProperty(content: String, modifier: Modifier): MarkdownProperty {
        val base = super.rememberMarkdownProperty(content, modifier)
        val components = remember {
            markdownComponents(
                heading6 = { model: MarkdownComponentModel ->
                    MarkdownText(
                        modifier = Modifier
                            .background(Color.Yellow)
                            .semantics { heading() },
                        content = model.content,
                        node = model.node,
                        style = model.typography.h6,
                        contentChildType = MarkdownTokenTypes.ATX_CONTENT,
                    )
                }
            )
        }

        return base.copy(components = components)
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
    @Composable
    override fun rememberMarkdownColors(): MarkdownColors {
        val colorScheme = MaterialTheme.colorScheme

        return remember {
            DefaultMarkdownColors(
                text = colorScheme.onBackground,
                codeBackground = colorScheme.surfaceVariant,
                inlineCodeBackground = colorScheme.surfaceVariant,
                dividerColor = colorScheme.outline,
                tableBackground = colorScheme.surface
            )
        }
    }

    @Composable
    override fun rememberMarkdownTypography(): MarkdownTypography {
        val colorScheme = MaterialTheme.colorScheme
        val typography = MaterialTheme.typography
        return remember {
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
    }
}

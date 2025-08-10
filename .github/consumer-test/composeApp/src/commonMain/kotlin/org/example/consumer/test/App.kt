package org.example.consumer.test

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath

@Composable
@Preview
fun App() {

    CompositionLocalProvider(
        LocalMarkdownTypography provides rememberMarkdownTypography(),
        LocalMarkdownColors provides rememberMarkdownColors()
    ) {
        MaterialTheme {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize().padding(horizontal = 48.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Contents.Compose()
                    Spacer(modifier = Modifier.height(16.dp))
                    Contents.PlainMarkdown()
                }
            }
        }
    }
}


class MyMarkdownRenderer : MarkdownRenderer {

    @Composable
    fun SampleCode(
        modifier: Modifier,
        source: String,
        content: @Composable (() -> Unit)
    ) {
        Row(
            modifier = modifier.height(IntrinsicSize.Min)
                .border(1.dp, Color.Black, RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
            Spacer(modifier = Modifier.width(4.dp))

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(0.8f),
                color = Color.Gray,
                thickness = 1.dp,
            )

            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier.weight(1f)
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = source,
                    style = LocalMarkdownTypography.current.inlineCode,
                )
            }
        }
    }

    @Composable
    override fun InlineComposableWrapper(
        modifier: Modifier,
        source: String,
        content: @Composable (() -> Unit)
    ) {
        when (source.lineSequence().first()) {
            "//!SampleCode" -> {
                SampleCode(
                    modifier = modifier.padding(8.dp),
                    source = source.lineSequence().drop(1).joinToString("\n"),
                    content = content
                )
            }

            else -> {
                content()
            }
        }
    }


    @Composable
    override fun Render(modifier: Modifier, path: String?, source: String) {

        Markdown(
            content = source,
            colors = LocalMarkdownColors.current,
            typography = LocalMarkdownTypography.current,
            modifier = Modifier,
            imageTransformer = Coil3ImageTransformerImpl,
            components = markdownComponents(
                codeBlock = highlightedCodeBlock,
                codeFence = highlightedCodeFence
            )
        )
    }
}


@GenerateMarkdownContents(MyMarkdownRenderer::class)
interface Contents {
    @Composable
    @GenerateMarkdownFromPath("PLAIN.md")
    fun PlainMarkdown()

    @Composable
    @GenerateMarkdownFromPath("Compose.mdcx")
    fun Compose()


    val contentsMap: Map<String, @Composable (Modifier) -> Unit>

    companion object : Contents by ContentsImpl
}


@Composable
fun rememberMarkdownColors(): MarkdownColors {
    val colorScheme = MaterialTheme.colorScheme

    val colors = remember {
        object : MarkdownColors {
            override val text: Color = colorScheme.onBackground
            override val codeText: Color = colorScheme.onSurface
            override val inlineCodeText: Color = colorScheme.onSurfaceVariant
            override val linkText: Color = colorScheme.primary
            override val codeBackground: Color = colorScheme.surfaceVariant
            override val inlineCodeBackground: Color = colorScheme.surfaceVariant
            override val dividerColor: Color = colorScheme.outline
            override val tableText: Color = colorScheme.onSurface
            override val tableBackground: Color = colorScheme.surface
        }
    }

    return colors
}

@Composable
fun rememberMarkdownTypography(): MarkdownTypography {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val markdownTypography = remember {
        object : MarkdownTypography {
            override val text: TextStyle = typography.bodyLarge
            override val code: TextStyle =
                typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            override val inlineCode: TextStyle =
                typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            override val h1: TextStyle = typography.headlineLarge
            override val h2: TextStyle = typography.headlineMedium
            override val h3: TextStyle = typography.headlineSmall
            override val h4: TextStyle = typography.titleLarge
            override val h5: TextStyle = typography.titleMedium
            override val h6: TextStyle = typography.titleSmall
            override val quote: TextStyle =
                typography.bodyLarge.copy(color = colorScheme.secondary)
            override val paragraph: TextStyle = typography.bodyLarge
            override val ordered: TextStyle = typography.bodyLarge
            override val bullet: TextStyle = typography.bodyLarge
            override val list: TextStyle = typography.bodyLarge
            override val link: TextStyle =
                typography.bodyLarge.copy(color = colorScheme.primary)
            override val textLink: TextLinkStyles = TextLinkStyles()
            override val table: TextStyle = typography.bodyMedium
        }
    }

    return markdownTypography
}
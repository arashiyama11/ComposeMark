package io.github.arashiyama11.composemark.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import io.github.arashiyama11.composemark.core.MarkdownRenderer

class MarkdownRendererImpl : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(modifier: Modifier, path: String?, source: String) {
        val color = rememberMarkdownColors()
        val typography = rememberMarkdownTypography()
        Markdown(
            content = source,
            modifier = modifier.verticalScroll(rememberScrollState()),
            colors = color,
            typography = typography
        )
    }

    @Composable
    override fun RenderComposableBlock(
        modifier: Modifier,
        path: String?,
        source: String,
        content: @Composable (() -> Unit)
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Gray),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = source)
            }
            HorizontalDivider()
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }

    @Composable
    override fun BlockContainer(modifier: Modifier, contents: List<@Composable (() -> Unit)>) {
        val state = rememberPagerState { contents.size }
        VerticalPager(state) {
            contents[it]()
        }
    }
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

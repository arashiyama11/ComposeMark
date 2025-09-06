package org.example.consumer.test

import androidx.compose.foundation.background
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
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromDirectory
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
                    modifier = Modifier.padding(horizontal = 48.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ContentsImpl.Compose(Modifier)
                }
            }
        }
    }
}


class MyComposeMark() : ComposeMark(MarkdownRendererImpl()) {
    override fun setup() {
        install(SampleCodePlugin) {
            orientation = SampleCodeConfig.Orientation.HORIZONTAL
        }
    }
}

@GenerateMarkdownContents(MyComposeMark::class)

interface Contents {

    //@GenerateMarkdownFromDirectory(".", includes = [], excludes = [])
    val contentsMap: Map<String, @Composable (Modifier) -> Unit>

    @Composable
    @GenerateMarkdownFromPath("PLAIN.md")
    fun PlainMarkdown(modifier: Modifier)

    @Composable
    @GenerateMarkdownFromPath("Compose.mdcx")
    fun Compose(modifier: Modifier)

    companion object : Contents by ContentsImpl
}


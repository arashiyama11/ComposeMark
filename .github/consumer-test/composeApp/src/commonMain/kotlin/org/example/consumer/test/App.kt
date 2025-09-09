package org.example.consumer.test

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import io.github.arashiyama11.composemark.core.ComposeMark
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.plugin.PageScaffoldPlugin
import io.github.arashiyama11.composemark.plugin.TocPosition

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
                    modifier = Modifier.padding(horizontal = 48.dp),
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

        install(PageScaffoldPlugin) {
            enableScroll(ScrollState(0), withToc = true)
            tocPosition = TocPosition.Right
            showBreadcrumbs = true
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


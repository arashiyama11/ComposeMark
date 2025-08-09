package org.example.consumer.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Contents.Compose()
        }
    }
}


class MyMarkdownRenderer : MarkdownRenderer {
    @Composable
    override fun Render(modifier: Modifier, path: String?, source: String) {
        Text(text = source, modifier = modifier)
    }
}


@GenerateMarkdownContents(MyMarkdownRenderer::class)
interface Contents {
    @Composable
    @GenerateMarkdownFromPath("mdcx/PLAIN.md")
    fun PlainMarkdown()

    @Composable
    @GenerateMarkdownFromPath("mdcx/Compose.mdcx")
    fun Compose()


    val contentsMap: Map<String, @Composable (Modifier) -> Unit>

    companion object : Contents by ContentsImpl
}


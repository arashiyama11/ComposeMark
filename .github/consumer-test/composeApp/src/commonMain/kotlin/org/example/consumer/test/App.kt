package org.example.consumer.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import org.jetbrains.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Contents.Readme()
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
    @GenerateMarkdownFromSource("README.md")
    fun Readme()

    val contentsMap: Map<String, @Composable (Modifier) -> Unit>

    companion object : Contents by ContentsImpl
}


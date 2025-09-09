package io.github.arashiyama11.composemark.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.plugin.PageScaffoldPlugin
import io.github.arashiyama11.composemark.plugin.TocPosition
import io.github.arashiyama11.composemark.sample.ui.theme.ComposeMarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeMarkTheme {
                Scaffold(
                    modifier = Modifier
                ) { contentPadding ->
                    Box(
                        Modifier
                            .padding(contentPadding)
                    ) {
                        Contents.README(
                            Modifier//.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

class MyComposeMark() : ComposeMark(MarkdownRendererImpl()) {
    override fun setup() {
        install(PageScaffoldPlugin) {
            enableScroll(ScrollState(0), withToc = true)
            tocPosition = TocPosition.Right
        }
    }
}


@GenerateMarkdownContents(MyComposeMark::class)
interface Contents {
    @Composable
    @GenerateMarkdownFromPath("README.md")
    fun README(modifier: Modifier = Modifier)

    companion object : Contents by ContentsImpl
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ComposeMarkTheme {
        Greeting("Android")
    }
}


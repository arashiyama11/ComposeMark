package io.github.arashiyama11.composemark.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.sample.ui.theme.ComposeMarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeMarkTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column { }
                    Contents.Readme() // これで README.md の内容が表示される
                }
            }
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
    @GenerateMarkdownFromPath("README.md")
    fun Readme()

    val contentsMap: Map<String, @Composable (Modifier) -> Unit>

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

package io.github.arashiyama11.composemark.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.sample.ui.theme.ComposeMarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeMarkTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) { contentPadding ->
                    Contents.LICENSE(
                        Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

class MyComposeMark() : ComposeMark(MarkdownRendererImpl()) {
    override fun setup() {
        install(HeaderConfigPlugin) {
            headerModifier = Modifier
                .padding(36.dp)
                .background(Color.DarkGray)
                .fillMaxSize()

            headerContent { title, modifier ->
                Box(
                    modifier = modifier
                        .background(Color.DarkGray)
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}


@GenerateMarkdownContents(MyComposeMark::class)
interface Contents {
    @Composable
    @GenerateMarkdownFromPath("LICENSE")
    fun LICENSE(modifier: Modifier = Modifier)

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


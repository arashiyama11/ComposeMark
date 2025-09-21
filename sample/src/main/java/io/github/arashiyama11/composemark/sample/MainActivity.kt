package io.github.arashiyama11.composemark.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.charleskorn.kaml.Yaml
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.plugin.frontmatter.ConfigDecodeResult
import io.github.arashiyama11.composemark.plugin.frontmatter.FrontMatterConfigPlugin
import io.github.arashiyama11.composemark.plugin.frontmatter.configOrNull
import io.github.arashiyama11.composemark.plugin.inline.InlineEmbedPlugin
import io.github.arashiyama11.composemark.plugin.scaffold.PageScaffoldPlugin
import io.github.arashiyama11.composemark.plugin.scaffold.TocPosition
import io.github.arashiyama11.composemark.sample.ui.theme.ComposeMarkTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

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
                            Modifier
                        )
                    }
                }
            }
        }
    }
}

@Serializable
data class HeaderConfig(
    val title: String,
    val theme: String,
)

class MyComposeMark() : ComposeMark(MarkdownRendererImpl()) {
    override fun setup() {
        install(FrontMatterConfigPlugin) {

            val yaml = Yaml.default
            configureDecoderRegistry {
                decoder<HeaderConfig>("toml") {
                    runCatching {
                        yaml.decodeFromString<HeaderConfig>(it.rawText)
                    }.fold(
                        onSuccess = {
                            ConfigDecodeResult.Success(it)
                        },
                        onFailure = {
                            ConfigDecodeResult.Failure(it.message ?: "Unknown error")
                        }
                    )
                }
            }

            frontMatterSurface { metadata, modifier, content ->
                val config by remember {
                    mutableStateOf(metadata.configOrNull<HeaderConfig>())
                }
                Column(modifier) {
                    if (config != null) {
                        Text(
                            text = config!!.title,
                            modifier = Modifier,
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                    content(
                        Modifier
                    )
                }

            }
        }
        install(InlineEmbedPlugin)
        install(PageScaffoldPlugin) {
            enableScroll(ScrollState(0), withToc = true)
            tocPosition = TocPosition.Right
        }
    }
}


@GenerateMarkdownContents(MyComposeMark::class)
interface Contents {
    @Composable
    @GenerateMarkdownFromPath("sample/README.md")
    fun README(modifier: Modifier)

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

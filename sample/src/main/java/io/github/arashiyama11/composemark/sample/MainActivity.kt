package io.github.arashiyama11.composemark.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.plugin.PageScaffoldPlugin
import io.github.arashiyama11.composemark.sample.ui.theme.ComposeMarkTheme
import kotlinx.coroutines.launch

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
                        Contents.LICENSE(
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
            scaffold { (headings, breadcrumbs, content, jumpTo), modifier ->
                Column(modifier) {
                    Row {
                        breadcrumbs.forEach {
                            Text(
                                text = it.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                modifier = Modifier.padding(4.dp)
                            )
                            if (it != breadcrumbs.last()) {
                                Text(
                                    text = " / ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }

                    val scope = rememberCoroutineScope()

                    headings.forEach {
                        Text(
                            text = it.text,
                            style = when (it.level) {
                                1 -> MaterialTheme.typography.bodyLarge
                                2 -> MaterialTheme.typography.bodyMedium
                                else -> MaterialTheme.typography.bodySmall
                            },
                            modifier = Modifier
                                .padding(
                                    start = ((it.level - 1) * 16).dp,
                                    top = 8.dp,
                                    bottom = 4.dp
                                )
                                .clickable {
                                    scope.launch {
                                        println("DEBUG: Clicked heading: ${it.text}")
                                        jumpTo(it)
                                    }
                                }
                        )
                    }

                    HorizontalDivider(Modifier.fillMaxWidth())
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                    ) {
                        content(Modifier)
                    }
                }
            }
        }
    }
}


@GenerateMarkdownContents(MyComposeMark::class)
interface Contents {
    @Composable
    @GenerateMarkdownFromPath("README.md")
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


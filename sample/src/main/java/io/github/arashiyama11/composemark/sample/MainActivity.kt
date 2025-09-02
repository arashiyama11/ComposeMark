package io.github.arashiyama11.composemark.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.ComposeMarkPlugin
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.PreProcessorMetadataKey
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
                    Contents.Readme(Modifier.verticalScroll(rememberScrollState())) // これで README.md の内容が表示される
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

    @Composable
    override fun RenderComposable(
        modifier: Modifier,
        source: String,
        content: @Composable (() -> Unit)
    ) {
        Column(modifier = modifier.background(Color.Gray)) {
            Text(text = source)
            content()
        }
    }
}

class MyComposeMark() : ComposeMark(MyMarkdownRenderer()) {

    override fun setup() {
        install(HeaderConfigPlugin) {
            headerModifier = Modifier
                .padding(36.dp)
                .background(Color.DarkGray)
                .fillMaxSize()

            headerContent { title ->
                Box(
                    modifier = Modifier
                        .background(Color.DarkGray)
                        .fillMaxSize()
                        .padding(16.dp)
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
    @GenerateMarkdownFromPath("README.md")
    fun Readme(modifier: Modifier = Modifier)

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


class RemoveHeaderConfig()

object RemoveHeaderPlugin : ComposeMarkPlugin<RemoveHeaderConfig> {
    override fun install(
        scope: ComposeMark,
        block: RemoveHeaderConfig.() -> Unit
    ) {
        scope.markdownPreProcessorPipeline.intercept {
            val md = it.markdown.lineSequence().filter {
                !it.startsWith("#")
            }.joinToString("\n")
            proceedWith(it.copy(markdown = md))
        }

        scope.renderPipeline.intercept { sub ->
            val new = sub.copy(metadata = sub.metadata) {
                MaterialTheme {
                    Box(modifier = Modifier) {
                        sub.content()
                    }
                }
            }
            proceedWith(new)
        }
    }
}

class HeaderConfigConfig {
    var headerModifier: Modifier = Modifier
    var headerContent: (@Composable (title: String) -> Unit)? = null

    fun headerContent(content: @Composable (title: String) -> Unit) {
        headerContent = content
    }
}

object HeaderConfigPlugin : ComposeMarkPlugin<HeaderConfigConfig> {
    val TitleKey = PreProcessorMetadataKey<String>("title")
    val ThemeKey = PreProcessorMetadataKey<String>("theme")

    override fun install(scope: ComposeMark, block: HeaderConfigConfig.() -> Unit) {
        val headerConfig = HeaderConfigConfig().apply(block)
        scope.markdownPreProcessorPipeline.intercept {
            it.metadata
            val headerSection =
                it.markdown.lineSequence().takeWhile { it != "---" }.joinToString("\n")
            if (headerSection.trim().isBlank() || headerSection == it.markdown) {
                proceed()
                return@intercept
            }
            val config = parseMarpFrontMatter(headerSection)
            if (config.containsKey("title")) {
                it.metadata[TitleKey] = config["title"]!!
            }
            if (config.containsKey("theme")) {
                it.metadata[ThemeKey] = config["theme"]!!
            }

            val md = it.markdown.lineSequence().dropWhile { it != "---" }.drop(1)
                .joinToString("\n")
            proceedWith(it.copy(markdown = md, metadata = it.metadata))
        }

        scope.renderPipeline.intercept {
            Log.d("Debug", "Title: ${it.metadata[TitleKey]}, Theme: ${it.metadata[ThemeKey]}")
            var subject = it.metadata[TitleKey]?.let { title ->
                it.copy(metadata = it.metadata) {
                    Column {
                        if (headerConfig.headerContent != null) {
                            headerConfig.headerContent!!.invoke(title)
                        } else {
                            Box(
                                modifier = headerConfig.headerModifier
                                    .background(MaterialTheme.colorScheme.primary)
                                    .fillMaxSize()
                            ) {
                                Text(
                                    text = title,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                )
                            }
                        }
                        it.content()
                    }

                }
            } ?: it

            proceedWith(subject)
            return@intercept

            subject = it.metadata[ThemeKey]?.let { theme ->
                subject.copy(metadata = it.metadata) {
                    MaterialTheme {
                        Box(
                            modifier = when (theme) {
                                "dark" -> Modifier.background(Color.DarkGray)
                                "light" -> Modifier.background(Color.White)
                                else -> Modifier
                            }
                        ) {
                            //it.content()
                        }
                    }
                }
            } ?: subject

            proceedWith(subject)
        }
    }

    fun parseMarpFrontMatter(input: String): Map<String, String> {
        fun stripInlineCommentOutsideQuotes(s: String): String {
            val sb = StringBuilder()
            var inSingle = false
            var inDouble = false
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { // エスケープは次文字をそのまま取り込む
                    sb.append(c)
                    sb.append(s[i + 1])
                    i += 2
                    continue
                }
                if (c == '\'' && !inDouble) {
                    inSingle = !inSingle
                    sb.append(c)
                    i++
                    continue
                }
                if (c == '"' && !inSingle) {
                    inDouble = !inDouble
                    sb.append(c)
                    i++
                    continue
                }
                if (c == '#' && !inSingle && !inDouble) {
                    // クォートの外側に来たコメント開始 -> 切る
                    break
                }
                sb.append(c)
                i++
            }
            return sb.toString().trim()
        }

        fun unquoteIfNeeded(s: String): String {
            if (s.length >= 2) {
                if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                    return s.substring(1, s.length - 1)
                }
            }
            return s
        }

        val result = linkedMapOf<String, String>()
        input.lineSequence().forEach { rawLine ->
            var line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("---")) return@forEach
            if (line.startsWith("#")) return@forEach

            val colonIndex = line.indexOf(':')
            if (colonIndex == -1) return@forEach

            val key = line.substring(0, colonIndex).trim()
            var valuePart = line.substring(colonIndex + 1).trim()
            valuePart = stripInlineCommentOutsideQuotes(valuePart)
            valuePart = unquoteIfNeeded(valuePart)
            result[key] = valuePart
        }
        return result
    }

}
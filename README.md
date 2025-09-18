# ComposeMark

ComposeMark is a KSP-based tool that turns Markdown files (and inline Markdown) into Jetpack Compose
`@Composable` functions. It also supports embedding Compose code inside Markdown and provides
hookable pipelines to customize preprocessing and rendering.

## Modules

- core: Annotations, pipelines, `ComposeMark`, and `MarkdownRenderer` APIs
- processor: KSP processor that generates Compose implementations

## Quick Start

- Apply KSP and (recommended) the ComposeMark Gradle plugin

```kotlin
// module build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
    id("io.github.arashiyama11.composemark") version "0.0.0-alpha03" // optional: configure watch patterns via composeMark {}
}

dependencies {
    implementation("io.github.arashiyama11:composemark-core:0.0.0-alpha03")
    ksp("io.github.arashiyama11:composemark-processor:0.0.0-alpha03")
}
```

- Note about defaults
    - The plugin sets a default root path (`projectDir`) and passes it to KSP as
      `composemark.root.path`.
    - It does not watch any files by default. Configure patterns as needed:

```kotlin
composeMark {
    rootPath = project.projectDir.path
    watch("docs/**/*.md", "docs/**/*.mdx") // relative to rootPath; optional
    ensureCommonKspBeforeKotlinCompile() // Ensure common metadata is generated before compile tasks
}
```

### Kotlin Multiplatform (Kotlin 2.x) setup

When using KMP with Kotlin 2.x, wire the generated metadata sources and ensure build order:

```kotlin
// module build.gradle.kts
dependencies {
    // Use the metadata-specific configuration in KMP
    kspCommonMainMetadata("io.github.arashiyama11:composemark-processor:0.0.0-alpha03")
}

kotlin {
    sourceSets.named("commonMain") {
        // Add KSP output to commonMain explicitly (KGP 2.x does not auto-register it)
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}
```

Tip: See `.github/consumer-test` for a complete, working configuration (KMP setup,
`kspCommonMainMetadata`, explicit `srcDir` registration for generated sources, and dependency
wiring).

### Implement a renderer and a `ComposeMark`

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.ComposeMark

class SimpleRenderer : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(modifier: Modifier, path: String?, source: String) {
        Text(source, modifier)
    }

    @Composable
    override fun RenderComposableBlock(
        modifier: Modifier,
        path: String?,
        source: String,
        content: @Composable () -> Unit
    ) {
        content()
    }
}

class MyComposeMark : ComposeMark(SimpleRenderer()) {
    override fun setup() { /* install plugins if needed */
    }
}
```

### Generate composables from Markdown

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource

@GenerateMarkdownContents(MyComposeMark::class)
interface Docs {
    @Composable
    @GenerateMarkdownFromPath("docs/intro.md")
    fun Intro(modifier: Modifier = Modifier)

    @Composable
    @GenerateMarkdownFromSource(
        """
    // Inline
    You can write Compose below.
    <Composable>
      androidx.compose.material3.Text("Hello from Compose block!!!!!!!!")
    </Composable>
  """.trimIndent()
    )
    fun Inline(modifier: Modifier = Modifier)

    companion object : Docs by DocsImpl
}
```

Build the project and call `Docs.Intro()` / `Docs.Inline()` like normal composables. Imports inside
`<Composable>/*...*/</Composable>` blocks are automatically lifted to the top of the generated file.

## Directory Aggregation (optional)

Generate functions from a folder and access them via a map. Declare a property with the following
type and annotate it.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromDirectory

@GenerateMarkdownContents(MyComposeMark::class)
interface DirDocs {
    @GenerateMarkdownFromDirectory(
        dir = "docs",
        includes = ["**/*.md", "**/*.mdx"],
        excludes = []
    )
    val contents: Map<String, @Composable (Modifier) -> Unit>

    companion object : DirDocs by DirDocsImpl
}
```

KSP reads files under `composemark.root.path/dir`. It generates one composable per file and an
`override val contents` mapping sanitized file stems (e.g., getting_started → GettingStarted()) to
those composables.

## Plugins and Pipelines

- ComposeMark subclass: Owns the pipelines and installs plugins.
- Pipelines: Interception points you can hook into:
    - Preprocess: markdown block, composable block, block list
    - Render: markdown block, composable block, block container
- Plugin API: Use `composeMarkPlugin` or `createComposeMarkPlugin` to register interceptors.
  Interceptors can read/modify the subject, attach metadata, call `proceedWith(...)`, or `finish()`.

Example: strip front matter and expose title

```kotlin
import io.github.arashiyama11.composemark.core.*

val TitleKey = PreProcessorMetadataKey<String>("title")

val FrontMatterPlugin = composeMarkPlugin({ Unit }) {
    onMarkdownBlockPreProcess { sub ->
        val header = sub.content.lineSequence().takeWhile { it != "---" }.joinToString("\n")
        val body = sub.content.lineSequence().dropWhile { it != "---" }.drop(1).joinToString("\n")
        if (header.isNotBlank()) sub.metadata[TitleKey] =
            header.lines().firstOrNull()?.removePrefix("title:")?.trim()
        proceedWith(sub.copy(content = body))
    }
}

class MyComposeMark : ComposeMark(SimpleRenderer()) {
    override fun setup() {
        install(FrontMatterPlugin)
    }
}
```

## Current Annotations

- GenerateMarkdownContents(composeMark: KClass<out ComposeMark>, implName: String = "")
- GenerateMarkdownFromPath(path: String)
- GenerateMarkdownFromSource(source: String)
- GenerateMarkdownFromDirectory(dir: String, includes: Array<String>, excludes: Array<String>) on a
  property of type `Map<String, @Composable (Modifier) -> Unit>`

## License

Apache-2.0

# ComposeMark

ComposeMark is a KSP-driven toolchain that turns Markdown files (and inline Markdown sections) into
Jetpack Compose `@Composable` functions. It lets you embed Compose code inside Markdown, inject your
own preprocessing/rendering steps, and ship higher level runtime helpers via optional plugins.

## Highlights
- Markdown → Compose generation for both file-based and inline sources
- Inline `<Composable>` blocks with automatic import extraction
- Pluggable preprocessing and rendering pipelines with metadata passing
- Directory aggregation that exposes generated composables as a map
- Bundled runtime plugins for front matter decoding, inline embeds, and page scaffolds

## Modules
- `core`: Runtime APIs (`ComposeMark`, pipelines, annotations, renderer contracts)
- `processor`: KSP processor + Gradle plugin (`io.github.arashiyama11.composemark`)
- `plugin`: Optional runtime plugins (front matter, inline embed helpers, page scaffold)
- `sample`: Demo Android app showcasing ComposeMark-generated composables

## Getting Started

### Gradle setup (single-platform / Android)
```kotlin
// module build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
    id("io.github.arashiyama11.composemark") version "0.0.0-alpha06" // optional but recommended
}

dependencies {
    implementation("io.github.arashiyama11:composemark-core:0.0.0-alpha06")
    ksp("io.github.arashiyama11:composemark-processor:0.0.0-alpha06")
}

composeMark {
    rootPath = project.projectDir.path // default; update when docs live elsewhere
    watch("docs/**/*.md", "docs/**/*.mdx") // optional KSP inputs for incremental builds
}

// Call when you need common metadata before any Kotlin compilation tasks (KMP projects especially).
composeMark.ensureCommonKspBeforeKotlinCompile()
```

The Gradle plugin wires the `composemark.root.path` KSP argument and attaches watched files to every
`ksp*` task for incremental/continuous builds. Patterns are resolved relative to `rootPath`.

### Kotlin Multiplatform (Kotlin 2.x)
```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.google.devtools.ksp")
    id("io.github.arashiyama11.composemark")
}

dependencies {
    kspCommonMainMetadata("io.github.arashiyama11:composemark-processor:0.0.0-alpha06")
}

kotlin {
    sourceSets.named("commonMain") {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

composeMark {
    rootPath = project.layout.projectDirectory.dir("docs").asFile.path
    watch("**/*.md", "**/*.mdx")
    ensureCommonKspBeforeKotlinCompile()
}
```
`composemark.root.path` is mandatory when using directory aggregation; otherwise file reads are
resolved relative to the project directory.

Tip: `.github/consumer-test` contains a fully wired KMP sample using the same configuration.

## Implementing a renderer and ComposeMark
```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.ComposeMark
import io.github.arashiyama11.composemark.core.MarkdownRenderer
import io.github.arashiyama11.composemark.core.RenderContext

class SimpleRenderer : MarkdownRenderer {
    @Composable
    override fun RenderMarkdownBlock(context: RenderContext, modifier: Modifier) {
        Text(context.source, modifier)
    }

    @Composable
    override fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit,
    ) {
        content()
    }
}

class MyComposeMark : ComposeMark(SimpleRenderer()) {
    override fun setup() {
        // install(CustomPlugin) { ... }
    }
}
```
`MarkdownRenderer.BlockContainer` defaults to a vertical `Column`; override it if you need custom
layout for mixed Markdown/Compose blocks.

## Generating composables from Markdown
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
        # Inline example
        You can write Compose blocks inline.
        <Composable>
            import androidx.compose.material3.Text

            Text("Hello from Compose block!")
        </Composable>
        """.trimIndent()
    )
    fun Inline(modifier: Modifier = Modifier)

    companion object : Docs by DocsImpl
}
```
`DocsImpl` is generated at build time. `<Composable>` blocks may include `import` statements which
are hoisted to the top of the generated file.

## Directory aggregation (optional)
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
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
Entries are keyed by a sanitized stem (e.g. `getting-started.md` → `GettingStarted`). Ensure
`composeMark.rootPath` points to a directory that contains `docs/`.

## Bundled runtime plugins (`io.github.arashiyama11:composemark-plugin`)
These helpers live in the `plugin` module and can be installed from `ComposeMark.setup()`.

### FrontMatterConfigPlugin
Parses YAML front matter, stores decoded values in pipeline metadata, and gives you a hook to wrap
rendered content. Configure decoders via `configureDecoderRegistry { }` or provide a custom
`FrontMatterConfigRegistry`.

### InlineEmbedPlugin
Transforms `[cm-inline:slotId]` placeholders inside Markdown into inline slots that accept Compose
content. Width/height/alignment can be tweaked with attributes (e.g. `<Composable attrs="width=dp:240">`).
Use `rememberInlineEmbedContent()` to build an `InlineTextContent` map when rendering text.

### PageScaffoldPlugin
Collects headings, auto-generates anchors, exposes breadcrumb metadata, and wraps the page with a
simple scaffold (configurable TOC position, numbering, scrolling). Override the scaffold and TOC
composables for full control.

## Pipelines & metadata
`ComposeMark` exposes pre-processing and rendering pipelines for markdown blocks, composable blocks,
and block lists. Plugins register interceptors via `composeMarkPlugin { ... }`, with optional
priorities/order, and can store structured data using `PreProcessorMetadataKey`.

```kotlin
install(composeMarkPlugin({ Unit }) {
    onMarkdownBlockPreProcess { subject ->
        val trimmed = subject.data.source.trimMargin()
        proceedWith(subject.copy(data = subject.data.copy(source = trimmed)))
    }
})
```
`subject.metadata` is shared downstream; access it inside renderers via `RenderContext.metadata`.

## License
Apache-2.0

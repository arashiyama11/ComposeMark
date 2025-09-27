# ComposeMark

ComposeMark is a Kotlin Multiplatform + KSP toolchain for turning Markdown documents (and inline
Markdown snippets) into Jetpack Compose `@Composable` functions. It lets you blend Markdown with
embedded Compose blocks, intercept every stage of the rendering pipeline, and ship optional runtime
helpers for common scenarios.

## Highlights

- Kotlin Multiplatform (JVM, Android, iOS, wasmJs) runtime paired with a KSP processor that
  generates composables from file-based or inline Markdown sources.
- Inline `<Composable>` blocks support optional attributes, lift their own `import` lines into the
  generated file, and run through the same preprocessing/render pipelines as Markdown blocks.
- Directory aggregation turns folders of Markdown into `Map<String, @Composable (Modifier) -> Unit>`
  entries with deterministic snake_case keys, duplicate-key detection, and explicit path tracking.
- Rich preprocessing/render pipelines with metadata propagation, plus a lightweight plugin DSL (
  `composeMarkPlugin { ... }`) for installing interceptors across Markdown, composable, and
  block-list stages.
- Optional runtime plugin module (`io.github.arashiyama11:composemark-plugin`) that provides front
  matter decoding, inline embed helpers, and page scaffold utilities on top of the core runtime.

## Modules

- `core`: Kotlin Multiplatform runtime, pipelines, annotations, and the `ComposeMark` base class.
- `processor`: KSP processor and Gradle plugin (`io.github.arashiyama11.composemark`) written in
  Kotlin/JVM.
- `plugin`: Optional Compose runtime plugins (front matter, inline embed, page scaffold) published
  separately.
- `sample`: Android demo app that consumes the local artifacts.
- `.github/consumer-test`: end-to-end consumer project that exercises JVM and browser targets.

## Gradle Setup

### Android / single-platform module

```kotlin
plugins {
    id("com.google.devtools.ksp")
    id("io.github.arashiyama11.composemark") version "0.0.0-alpha07" // optional but recommended
}

dependencies {
    implementation("io.github.arashiyama11:composemark-core:0.0.0-alpha07")
    ksp("io.github.arashiyama11:composemark-processor:0.0.0-alpha07")
}

composeMark {
    rootPath = project.projectDir.absolutePath // defaults to projectDir
    watch("docs/**/*.md", "docs/**/*.mdx")      // optional: wires into every ksp* task input
}
```

The Gradle plugin automatically:

- feeds `composemark.root.path` into the KSP extension (defaulting to `projectDir`)
- attaches any `watch(...)` patterns (resolved relative to `rootPath`) as incremental inputs to all
  `ksp*` tasks.

### Kotlin Multiplatform (Kotlin 2.x)

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.google.devtools.ksp")
    id("io.github.arashiyama11.composemark") version "0.0.0-alpha07"
}

kotlin {
    sourceSets.named("commonMain") {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

dependencies {
    kspCommonMainMetadata("io.github.arashiyama11:composemark-processor:0.0.0-alpha07")
}

composeMark {
    rootPath = project.layout.projectDirectory.dir("docs").asFile.path
    watch("**/*.md", "**/*.mdx")
    ensureCommonKspBeforeKotlinCompile() // wires kspCommonMainKotlinMetadata before compile tasks
}

// Recommended to keep Kotlin compile tasks waiting for metadata generation:
tasks.matching { it.name.startsWith("compileKotlin") }
    .configureEach { it.dependsOn("kspCommonMainKotlinMetadata") }
```

`composemark.root.path` must resolve to the directory where your Markdown lives whenever you rely on
directory aggregation. All file reads are relative to this path.

Refer to `.github/consumer-test` for an end-to-end KMP configuration.

## Generating Composables

Annotate an interface with `@GenerateMarkdownContents`, describe each piece of Markdown via the path
or inline annotations, and delegate to the generated implementation:

```kotlin
@GenerateMarkdownContents(MyComposeMark::class)
interface Docs {
    @Composable
    @GenerateMarkdownFromPath("docs/intro.md")
    fun Intro(modifier: Modifier = Modifier)

    @Composable
    @GenerateMarkdownFromSource(
        """
        # Inline example
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

Key behaviours:

- The generated class defaults to `InterfaceNameImpl`; supply `implName = "MyDocs"` on
  `@GenerateMarkdownContents` to override it.
- Inline `<Composable>` sections may declare their own `import` (and optional `import ... as Alias`)
  statements; these are hoisted to the top of the generated file.
- Function bodies accept the same signature as the abstract declarations. Only functions whose
  first (and only) parameter is `Modifier` are registered in the generated `contents` map.

## Directory Aggregation

Add a property annotated with `@GenerateMarkdownFromDirectory` to aggregate a folder:

```kotlin
@GenerateMarkdownContents(MyComposeMark::class)
interface DirDocs {
    @GenerateMarkdownFromDirectory(
        dir = "docs",
        includes = ["**/*.md", "**/*.mdx"],
        excludes = ["drafts/**"]
    )
    val contents: Map<String, @Composable (Modifier) -> Unit>

    companion object : DirDocs by DirDocsImpl
}
```

Runtime characteristics:

- Keys are derived from the stem of each file, normalized to `snake_case` (`getting-started.md` →
  `getting_started`). Duplicate keys fail the KSP round with an error.
- Only files that match `includes` and do not match `excludes` are processed. At least one match is
  required (otherwise the processor reports an error).
- Files are read relative to `composemark.root.path`; the processor fails fast if the directory is
  missing.

Conflicts between directory-derived function names and user-defined abstract functions are skipped
to avoid double generation.

## ComposeMark Runtime

`ComposeMark` wraps a `MarkdownRenderer` (defaults backed by `multiplatform-markdown-renderer`) and
exposes hooks for every stage:

- `markdownBlockPreProcessorPipeline`, `composableBlockPreProcessorPipeline`, and
  `blockListPreProcessorPipeline`
- `renderMarkdownBlockPipeline`, `renderComposableBlockPipeline`, and `renderBlocksPipeline`

Each pipeline carries a mutable `PreProcessorMetadata` map which is snapshot into a
`CompositionLocal` (`RenderContext.metadata`) so renderers and plugins can share structured data.

Utility types:

- `Block.markdown(...)` and `Block.composable(...)` let you mix Markdown and Compose blocks
  explicitly and render them via `ComposeMark.RenderBlocks(...)`.
- `composeMarkPlugin { ... }` registers interceptors with `PipelinePriority` and `order` hints;
  plugins can opt-in to every stage without subclassing.

## Bundled Runtime Plugins (`io.github.arashiyama11:composemark-plugin`)

Install these from `ComposeMark.setup()`:

- **FrontMatterConfigPlugin**: parses YAML front matter, stores values in metadata, and exposes a
  configurable wrapper around rendered content.
- **InlineEmbedPlugin**: transforms `[cm-inline:slot]` placeholders into inline slots and exposes
  helpers to build `InlineTextContent`.
- **PageScaffoldPlugin**: collects headings, generates anchors, and wraps the document in a simple
  scaffold with optional table-of-contents composition.

These plugins are multiplatform and depend only on the `core` runtime.

## Gradle Plugin Notes

- Exposes the `composeMark` extension with `rootPath` and `watch(...)`.
- Sets the `ksp.composemark.root.path` extra property and forwards it into the KSP extension.
- Marks all files matched by `watch(...)` as inputs to every `ksp*` task with
  `PathSensitivity.RELATIVE`.
- Provides `Project.ensureCommonKspBeforeKotlinCompile()` helper to ensure metadata KSP tasks run
  before Kotlin compilation (useful for KMP builds).

## License

Apache-2.0

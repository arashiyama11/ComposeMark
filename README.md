theme: androidx.compose.material3.MaterialTheme
title: Hello ComposeMark
footer: &copy; 2024 arashiyama11
---

# ComposeMark

ComposeMark は、Markdown ファイルやインライン Markdown を Jetpack Compose の `@Composable`
関数として自動生成する KSP ベースのライブラリです。ユーザーは簡潔なアノテーションとインターフェース定義を記述するだけで、Markdown→UI
のパイプラインを構築できます。

## モジュール構成

* **core**: アノテーション定義・`MarkdownRenderer` の基盤定義
* **processor**: KSP Processor 実装 (`MarkdownComposeProcessor`)

<Composable>
  androidx.compose.material3.Text("Hello ComposeMark!")
</Composable>

## アノテーション

ComposeMark は以下の主要なアノテーションを提供します。

* `@GenerateMarkdownFromPath`: 関数に Markdown ファイルパスを紐付けます。
* `@GenerateMarkdownFromSource`: 関数にインライン Markdown ソースを紐付けます。
* `@GenerateMarkdownContents`: 対象インターフェース／クラスに対して、Composable 実装を一括生成します。

## MarkdownRenderer

`MarkdownRenderer` は、Markdown テキストを描画するための単一メソッドインターフェースです。

```kotlin
package io.github.arashiyama11.composemark.core

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable

/** Markdown テキストを描画するレンダラー */
fun interface MarkdownRenderer {
    @Composable
    fun render(modifier: Modifier, text: String)
}
```

`render` メソッドの `text` 引数には、Markdown の生テキストが渡されます。Markdown のパースと描画ロジックは
`render` メソッドの実装に委ねられます。

## KSP Processor の動作概要

KSP Processor は、`@GenerateMarkdownContents` アノテーションが付与されたクラスを探索し、その中の
`@GenerateMarkdownFromPath` または `@GenerateMarkdownFromSource` を持つ関数を収集します。収集した情報に基づき、Markdown
テキストを読み込み、対応する `@Composable` 関数を自動生成します。

生成されるコードは、`object <ClassName>Impl : <ClassName>` の形式で、各関数をオーバーライドし、
`MarkdownRenderer` を使用してMarkdownを描画します。

## 使用例

```kotlin
import io.github.arashiyama11.composemark.core.DefaultMarkdownRenderer
import io.github.arashiyama11.composemark.core.annotation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@GenerateMarkdownContents(DefaultMarkdownRenderer::class)
interface Docs {

    @Preview
    @Composable
    @GenerateMarkdownFromPath("docs/intro.md")
    fun Intro()

    @Composable
    @GenerateMarkdownFromSource(
        "# インライン\n**Markdown** を直接書く例"
    )
    fun Preview(modifier: Modifier = Modifier)

    companion object : Docs by DocsImpl
}
```

上記の例では、`DocsImpl` が自動生成され、`Docs.Intro()` や `Docs.Preview()` で Composable
を呼び出すことができます。`@Preview` などのアノテーションは生成後の関数に引き継がれるため、Android
Studio でプレビュー可能です。

## ビルドとテスト

プロジェクトのビルドとテストはGradleコマンドで実行できます。

```bash
./gradlew build
./gradlew test
```

## ライブラリの公開

Maven Centralへの公開については、[README.md](README.md)の既存のセクションを参照してください。

## ライブラリの使い方

### 依存関係の設定

ルートの `build.gradle.kts` に `mavenCentral()` が含まれていることを確認してください。

```kotlin
// build.gradle.kts
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

次に、モジュールの `build.gradle.kts` に以下の依存関係を追加します。

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("io.github.arashiyama11:composemark-core:LATEST_VERSION")
    ksp("io.github.arashiyama11:composemark-processor:LATEST_VERSION")
}
```

`LATEST_VERSION`
は、[Maven Central](https://search.maven.org/artifact/io.github.arashiyama11/composemark-core)
で利用可能な最新のバージョンに置き換えてください。

### `MarkdownRenderer` の実装

Markdown をどのように Jetpack Compose の Composable に変換するかを定義する `MarkdownRenderer`
インターフェースを実装します。

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.arashiyama11.composemark.core.MarkdownRenderer

class CustomMarkdownRenderer : MarkdownRenderer {
    @Composable
    override fun Render(modifier: Modifier, text: String) {
        // ここでMarkdownをパースし、対応するComposableを呼び出します
        // この例では、単純にテキストを表示します
        Text(text = text, modifier = modifier)
    }
}
```

### インターフェースの定義

`@GenerateMarkdownContents` アノテーションを使用して、Markdown ソースを Composable
関数にマッピングするインターフェースを定義します。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownContents
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromPath
import io.github.arashiyama11.composemark.core.annotation.GenerateMarkdownFromSource

@GenerateMarkdownContents(CustomMarkdownRenderer::class)
interface MyMarkdownDocs {

    @Preview
    @Composable
    @GenerateMarkdownFromPath("path/to/your/markdown.md")
    fun MarkdownDocument()

    @Composable
    @GenerateMarkdownFromSource(
        """
        # Hello, ComposeMark!
        This is an example of inline Markdown.
        - List item 1
        - List item 2
        """
    )
    fun InlineMarkdown(modifier: Modifier = Modifier)

    companion object : MyMarkdownDocs by MyMarkdownDocsImpl
}
```

### Composable の利用

ビルド後、KSP によって `MyMarkdownDocsImpl` オブジェクトが生成されます。これにより、インターフェースで定義した関数を
Composable として直接呼び出すことができます。

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

@Composable
fun MyScreen() {
    Column {
        MyMarkdownDocs.MarkdownDocument()
        MyMarkdownDocs.InlineMarkdown()
    }
}
```

## APIリファレンス

### アノテーション

* `@GenerateMarkdownContents(renderer: KClass<out MarkdownRenderer>)`
    * **ターゲット:** `CLASS`
    * **説明:** 指定された `MarkdownRenderer` を使用して、インターフェース内の
      `@GenerateMarkdownFromPath` および `@GenerateMarkdownFromSource` アノテーションが付けられた関数の
      Composable 実装を生成します。
* `@GenerateMarkdownFromPath(path: String)`
    * **ターゲット:** `FUNCTION`
    * **説明:** Composable 関数に、指定されたパスの Markdown
      ファイルを関連付けます。パスはプロジェクトのルートディレクトリからの相対パスです。
* `@GenerateMarkdownFromSource(source: String)`
    * **ターゲット:** `FUNCTION`
    * **説明:** Composable 関数に、インラインの Markdown 文字列を関連付けます。

### インターフェース

* `MarkdownRenderer`
    * **説明:** Markdown テキストを Jetpack Compose の Composable に描画するためのインターフェースです。
    * **メソッド:**
        * `@Composable fun Render(modifier: Modifier, text: String)`
            * `modifier`: Composable に適用する `Modifier`。
            * `text`: 描画する生の Markdown テキスト。

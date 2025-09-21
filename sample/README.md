---
title: ComposeMark Sample
theme: default
---

# ComposeMark Sample アプリ

この画面は ComposeMark ライブラリで Markdown を Compose UI
に変換する手順を確認するためのサンプルです。フロントマターで指定したタイトルやテーマ、ページ内に自動生成される目次など、プラグイン連携のイメージをそのまま確認できます。

> ヒント: README を編集してビルドすると、変更内容がすぐにプレビューに反映されます。

## このサンプルで分かること

- `FrontMatterConfigPlugin` により `title` / `theme` をヘッダーへ反映する方法
- `PageScaffoldPlugin` によるスクロール + 右側目次 (TOC) 表示
- `InlineEmbedPlugin` を介したインライン埋め込みの下準備 (`[cm-inline:*]` プレースホルダー) の挙動
- `@GenerateMarkdownContents` / `@GenerateMarkdownFromPath` を使った Markdown → Composable の自動生成フロー

## 起動とビルド

1. `./gradlew :sample:assembleDebug` を実行するとアプリの APK が生成されます。
2. Android Studio から `app` を選択して起動するか、
   `adb install -r sample/build/outputs/apk/debug/sample-debug.apk` で端末へ配置してください。
3. README を更新した後は再ビルドすると ComposeMark が再生成したコンテンツが反映されます。

## Markdown と Compose の連携ポイント

### Front matter

先頭の `---` 区間に `title` と `theme` を記述すると、`HeaderConfig`
データクラスにデコードされヘッダーに表示されます。必須ではありませんが、空にするとタイトルが非表示になります。

### 自動生成された Composable

`Contents.README()` は KSP によって生成された Composable で、この README の Markdown をレンダリングします。
`sample/README.md` を編集すると、自動的に対応する Composable が再生成されます。

### Inline Embed の利用

`InlineEmbedPlugin` は `< Composable inline ...>` ブロックを文中へ差し込み、Markdown 内に
`[cm-inline:***]` プレースホルダーを挿入します。実際の埋め込みを行う場合は、対象ブロック内で Compose
UI を記述し、サイズや縦位置を属性 (`width="96.dp"` など) で指定してください。

## コンテンツ編集のコツ

- 長文になる場合は `##` / `###` を活用して目次を整理すると読みやすくなります。
- 画像やコードブロックもそのまま Markdown 記法で記述可能です。アプリ側では `MarkdownRendererImpl`
  がリンク色やタイポグラフィを調整しています。
- 生成済みのコンテンツを別画面で再利用したい場合は、`Contents` インターフェースを参照して任意の
  Composable から呼び出せます。

## 関連リンク

- ライブラリ本体: `core/` (KMP ランタイム) / `processor/` (KSP プロセッサ)
- デモアプリ: `sample/` (この README を表示)
- Consumer テスト: `.github/consumer-test/` (ローカル公開物の動作検証)

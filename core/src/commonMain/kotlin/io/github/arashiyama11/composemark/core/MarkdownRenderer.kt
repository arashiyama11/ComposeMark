package io.github.arashiyama11.composemark.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.MarkdownSuccess
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.model.DefaultMarkdownInlineContent
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownAnimations
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownExtendedSpans
import com.mikepenz.markdown.model.MarkdownInlineContent
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.markdownPadding
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

public interface MarkdownRenderer {
    @Composable
    public fun rememberMarkdownColors(): MarkdownColors

    @Composable
    public fun rememberMarkdownTypography(): MarkdownTypography

    @Composable
    public fun rememberMarkdownProperty(content: String, modifier: Modifier): MarkdownProperty {
        return MarkdownProperty.rememberDefault(
            content = content,
            colors = rememberMarkdownColors(),
            typography = rememberMarkdownTypography(),
            modifier = modifier,
        )
    }

    @Composable
    public fun RenderComposableBlock(
        context: RenderContext,
        modifier: Modifier,
        content: @Composable () -> Unit,
    )

    @Composable
    public fun BlockContainer(
        modifier: Modifier,
        contents: List<BlockEntry>
    ) {
        Column(modifier) {
            contents.forEach { it.content(Modifier) }
        }
    }
}

public data class MarkdownProperty(
    val content: String,
    val colors: MarkdownColors,
    val typography: MarkdownTypography,
    val modifier: Modifier = Modifier.fillMaxSize(),
    val padding: MarkdownPadding,
    val dimens: MarkdownDimens,
    val flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
    val parser: MarkdownParser = MarkdownParser(flavour),
    val imageTransformer: ImageTransformer = NoOpImageTransformerImpl(),
    val annotator: MarkdownAnnotator = markdownAnnotator(),
    val extendedSpans: MarkdownExtendedSpans,
    val inlineContent: MarkdownInlineContent = DefaultMarkdownInlineContent(emptyMap()),
    val components: MarkdownComponents = markdownComponents(),
    val animations: MarkdownAnimations,
    val referenceLinkHandler: ReferenceLinkHandler = ReferenceLinkHandlerImpl(),
    val loading: @Composable (modifier: Modifier) -> Unit = { Box(modifier) },
    val success: @Composable (state: State.Success, components: MarkdownComponents, modifier: Modifier) -> Unit = { state, components, modifier ->
        MarkdownSuccess(state = state, components = components, modifier = modifier)
    },
    val error: @Composable (modifier: Modifier) -> Unit = { Box(modifier) },
) {
    @Composable
    public fun Render() {
        Markdown(
            content = content,
            colors = colors,
            typography = typography,
            modifier = modifier,
            padding = padding,
            dimens = dimens,
            flavour = flavour,
            parser = parser,
            imageTransformer = imageTransformer,
            annotator = annotator,
            extendedSpans = extendedSpans,
            inlineContent = inlineContent,
            components = components,
            animations = animations,
            referenceLinkHandler = referenceLinkHandler,
            loading = loading,
            success = success,
            error = error
        )
    }

    public companion object Companion {
        @Composable
        public fun rememberDefault(
            content: String,
            colors: MarkdownColors,
            typography: MarkdownTypography,
            modifier: Modifier = Modifier.fillMaxSize(),
            padding: MarkdownPadding = markdownPadding(),
            dimens: MarkdownDimens = markdownDimens(),
            flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor(),
            parser: MarkdownParser = MarkdownParser(flavour),
            imageTransformer: ImageTransformer = NoOpImageTransformerImpl(),
            annotator: MarkdownAnnotator = markdownAnnotator(),
            extendedSpans: MarkdownExtendedSpans = markdownExtendedSpans(),
            inlineContent: MarkdownInlineContent = markdownInlineContent(),
            components: MarkdownComponents = markdownComponents(),
            animations: MarkdownAnimations = markdownAnimations(),
            referenceLinkHandler: ReferenceLinkHandler = ReferenceLinkHandlerImpl(),
            loading: @Composable (modifier: Modifier) -> Unit = { Box(modifier) },
            success: @Composable (state: State.Success, components: MarkdownComponents, modifier: Modifier) -> Unit = { state, components, modifier ->
                MarkdownSuccess(state = state, components = components, modifier = modifier)
            },
            error: @Composable (modifier: Modifier) -> Unit = { Box(modifier) },
        ): MarkdownProperty {
            return remember {
                MarkdownProperty(
                    content = content,
                    colors = colors,
                    typography = typography,
                    modifier = modifier,
                    padding = padding,
                    dimens = dimens,
                    flavour = flavour,
                    parser = parser,
                    imageTransformer = imageTransformer,
                    annotator = annotator,
                    extendedSpans = extendedSpans,
                    inlineContent = inlineContent,
                    components = components,
                    animations = animations,
                    referenceLinkHandler = referenceLinkHandler,
                    loading = loading,
                    success = success,
                    error = error
                )
            }
        }

    }
}

public data class BlockEntry(
    val context: RenderContext,
    val content: @Composable (Modifier) -> Unit,
)
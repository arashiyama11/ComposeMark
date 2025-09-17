package io.github.arashiyama11.composemark.plugin.inline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

context(density: Density)
internal fun autoMeasuredInlineTextContent(
    width: TextUnit? = null,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    placeholderVerticalAlign: PlaceholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
    children: @Composable () -> Unit,
): InlineTextContent {
    val resolvedFontSize = textStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp
    val resolvedLine =
        textStyle.lineHeight.takeIf { it != TextUnit.Unspecified } ?: (resolvedFontSize * 1.15f)

    var measuredWidth = resolvedFontSize

    val phWidth = width ?: measuredWidth
    val phHeight = resolvedLine

    return InlineTextContent(
        placeholder = Placeholder(phWidth, phHeight, placeholderVerticalAlign)
    ) {
        Box(
            modifier = if (width != null) {
                val wDp: Dp = with(density) { width.toPx().dp }
                Modifier.width(wDp)
            } else Modifier
        ) {
            Box(Modifier.onSizeChanged { sz ->
                with(density) { measuredWidth = sz.width.toFloat().toSp() }
            }) {
                children()
            }
        }
    }
}




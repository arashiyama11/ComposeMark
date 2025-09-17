package org.example.consumer.test

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.arashiyama11.composemark.core.composeMarkPlugin

class SampleCodeConfig {

    var command = "//!SampleCode"

    enum class Orientation {
        VERTICAL, HORIZONTAL
    }

    var orientation: Orientation = Orientation.VERTICAL
}

val SampleCodePlugin = composeMarkPlugin(::SampleCodeConfig) { config ->
    onRenderComposableBlock {
        if (it.context.source.lineSequence().firstOrNull()?.trim() != config.command) {
            proceed()
            return@onRenderComposableBlock
        }
        val source = it.context.source.lineSequence().drop(1).joinToString("\n")
        val result = it.copy(context = it.context.copy(source = source)) { modifier ->
            when (config.orientation) {
                SampleCodeConfig.Orientation.VERTICAL -> {
                    Column(modifier) {
                        Text(source)
                        it.content(Modifier)
                    }
                }

                SampleCodeConfig.Orientation.HORIZONTAL -> {
                    Row(
                        modifier.height(IntrinsicSize.Min)
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp)).padding(4.dp)
                    ) {
                        Text(
                            source,
                            Modifier.weight(1f).padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            softWrap = false
                        )

                        VerticalDivider(Modifier.fillMaxHeight())

                        Box(
                            Modifier.weight(1f).fillMaxHeight().padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            it.content(Modifier)
                        }
                    }
                }
            }
        }

        proceedWith(result)
    }
}
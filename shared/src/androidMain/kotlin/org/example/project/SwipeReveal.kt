package org.example.project

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun SwipeReveal(
    key: Any,
    actionsWidth: Dp,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val actionsWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { actionsWidth.toPx() }
    var revealedWidthPx by remember(key) { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).width(actionsWidth).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) { actions() }
        Box(
            modifier = Modifier
                .width(maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { revealedWidthPx.toDp() })
                .align(Alignment.CenterStart)
                .pointerInput(key, actionsWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            revealedWidthPx = (revealedWidthPx - dragAmount).coerceIn(0f, actionsWidthPx)
                        },
                        onDragEnd = { revealedWidthPx = if (revealedWidthPx > actionsWidthPx / 2) actionsWidthPx else 0f },
                        onDragCancel = { revealedWidthPx = if (revealedWidthPx > actionsWidthPx / 2) actionsWidthPx else 0f },
                    )
                },
        ) { content() }
    }
}

package com.chooseme.forever.ui.discover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SwipeDirection { LEFT, RIGHT }

@Composable
fun SwipeableCard(
    onSwiped: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (dragRatio: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val thresholdPx = screenWidthPx * 0.35f

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val dragRatio = (offsetX.value / screenWidthPx).coerceIn(-1f, 1f)

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .graphicsLayer {
                rotationZ = offsetX.value * 0.035f
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > thresholdPx) {
                                        offsetX.animateTo(
                                            targetValue = screenWidthPx * 1.5f,
                                            animationSpec = tween(durationMillis = 250)
                                        )
                                        onSwiped(SwipeDirection.RIGHT)
                                    } else if (offsetX.value < -thresholdPx) {
                                        offsetX.animateTo(
                                            targetValue = -screenWidthPx * 1.5f,
                                            animationSpec = tween(durationMillis = 250)
                                        )
                                        onSwiped(SwipeDirection.LEFT)
                                    } else {
                                        launch { offsetX.animateTo(0f, spring()) }
                                        launch { offsetY.animateTo(0f, spring()) }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo(offsetY.value + dragAmount.y)
                                }
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        content(dragRatio)
    }
}

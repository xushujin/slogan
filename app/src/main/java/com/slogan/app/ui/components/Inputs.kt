package com.slogan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slogan.app.ui.theme.LocalSloganPalette
import kotlin.math.roundToInt

private val ThumbRadius = 13.dp

/**
 * 自绘拟态滑杆：轨道内凹、滑块凸起，按下/拖动时按步长量化。
 * 不使用 Material Slider，避免出现与拟态风格冲突的水波纹和默认配色。
 */
@Composable
fun NeumorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 0f,
    enabled: Boolean = true,
) {
    val palette = LocalSloganPalette.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun emit(positionX: Float, paddingPx: Float) {
        if (!enabled || widthPx <= 0f) return
        val usable = (widthPx - paddingPx * 2f).coerceAtLeast(1f)
        var next = ((positionX - paddingPx) / usable).coerceIn(0f, 1f)
        if (step > 0f) {
            val raw = valueRange.start + next * span
            next = (((raw / step).roundToInt() * step) - valueRange.start) / span
            next = next.coerceIn(0f, 1f)
        }
        onValueChange(valueRange.start + next * span)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, valueRange, step) {
                detectTapGestures { offset -> emit(offset.x, ThumbRadius.toPx()) }
            }
            .pointerInput(enabled, valueRange, step) {
                detectHorizontalDragGestures { change, _ ->
                    emit(change.position.x, ThumbRadius.toPx())
                }
            },
    ) {
        val padding = ThumbRadius.toPx()
        val trackHeight = 14.dp.toPx()
        val centerY = size.height / 2f
        val usable = (size.width - padding * 2f).coerceAtLeast(1f)
        val thumbX = padding + usable * fraction
        val alpha = if (enabled) 1f else 0.4f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.shadowDark.copy(alpha = 0.55f * alpha),
                    palette.shadowLight.copy(alpha = 0.75f * alpha),
                ),
            ),
            topLeft = Offset(padding, centerY - trackHeight / 2f),
            size = Size(usable, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )

        if (fraction > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.55f * alpha),
                        palette.accent.copy(alpha = 0.9f * alpha),
                    ),
                ),
                topLeft = Offset(padding, centerY - trackHeight / 2f),
                size = Size(usable * fraction, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
        }

        drawCircle(
            color = palette.shadowDark.copy(alpha = 0.35f * alpha),
            radius = ThumbRadius.toPx(),
            center = Offset(thumbX, centerY + 2.dp.toPx()),
        )
        drawCircle(
            color = palette.surface,
            radius = ThumbRadius.toPx(),
            center = Offset(thumbX, centerY),
        )
        drawCircle(
            color = palette.accent.copy(alpha = alpha),
            radius = ThumbRadius.toPx(),
            center = Offset(thumbX, centerY),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = palette.accent.copy(alpha = 0.85f * alpha),
            radius = 4.dp.toPx(),
            center = Offset(thumbX, centerY),
        )
    }
}

@Composable
fun NeumorphicStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    val palette = LocalSloganPalette.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeumorphicIconButton(onClick = onDecrease, enabled = decreaseEnabled, size = 42.dp) {
            SloganIcon(glyph = SloganGlyph.Minus, size = 16.dp)
        }
        Text(
            text = valueText,
            modifier = Modifier.widthIn(min = 52.dp),
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        NeumorphicIconButton(onClick = onIncrease, enabled = increaseEnabled, size = 42.dp) {
            SloganIcon(glyph = SloganGlyph.Plus, size = 16.dp)
        }
    }
}

@Composable
fun NeumorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 3,
    maxLines: Int = 6,
    maxLength: Int = 500,
) {
    val palette = LocalSloganPalette.current
    BasicTextField(
        value = value,
        onValueChange = { next -> if (next.length <= maxLength) onValueChange(next) },
        modifier = modifier
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(18.dp),
                elevation = 0.dp,
                pressed = true,
            )
            .padding(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
        cursorBrush = SolidColor(palette.accent),
        minLines = minLines,
        maxLines = maxLines,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = palette.textSecondary.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSloganPalette.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = label)
            Text(
                text = valueText,
                color = palette.accent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        NeumorphicSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            step = step,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

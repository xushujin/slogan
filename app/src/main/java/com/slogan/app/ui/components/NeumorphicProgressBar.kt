package com.slogan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.slogan.app.ui.theme.LocalSloganPalette

/**
 * 拟态进度条：凹槽用「上暗下亮」模拟内陷，填充部分用主色渐变。
 * 与其它拟态组件一样，全部用 Canvas 自绘，不依赖 Material 的 ProgressIndicator。
 */
@Composable
fun NeumorphicProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val palette = LocalSloganPalette.current
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxWidth().height(16.dp)) {
        val inset = 1.5f
        val innerHeight = (size.height - inset * 2f).coerceAtLeast(0f)
        val radius = CornerRadius(innerHeight / 2f, innerHeight / 2f)

        drawRoundRect(
            color = palette.shadowDark.copy(alpha = 0.45f),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
        drawRoundRect(
            color = palette.shadowLight.copy(alpha = 0.55f),
            topLeft = Offset(inset, inset),
            size = Size((size.width - inset * 2f).coerceAtLeast(0f), innerHeight),
            cornerRadius = radius,
        )

        if (clamped <= 0f) return@Canvas
        val fillWidth = (size.width - inset * 2f) * clamped
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(palette.accent.copy(alpha = 0.72f), palette.accent),
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(inset, inset),
            size = Size(fillWidth.coerceAtLeast(0f), innerHeight),
            cornerRadius = radius,
        )
    }
}

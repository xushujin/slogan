package com.slogan.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自绘图标集。刻意不依赖 material-icons，保证图形风格统一、包体最小。
 * 所有路径都按 24×24 坐标系绘制，再按实际尺寸缩放。
 */
enum class SloganGlyph {
    Play,
    Stop,
    Plus,
    Minus,
    Trash,
    Tune,
    Info,
    Volume,
    Check,
    ChevronRight,
    Save,
    Megaphone,
}

@Composable
fun SloganIcon(
    glyph: SloganGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            when (glyph) {
                SloganGlyph.Play -> {
                    val path = Path().apply {
                        moveTo(8f, 5f)
                        lineTo(19f, 12f)
                        lineTo(8f, 19f)
                        close()
                    }
                    drawPath(path, color = tint)
                }

                SloganGlyph.Stop -> drawRoundRect(
                    color = tint,
                    topLeft = Offset(7f, 7f),
                    size = Size(10f, 10f),
                    cornerRadius = CornerRadius(2f),
                )

                SloganGlyph.Plus -> {
                    drawLine(tint, Offset(12f, 6f), Offset(12f, 18f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(6f, 12f), Offset(18f, 12f), strokeWidth = 2f, cap = StrokeCap.Round)
                }

                SloganGlyph.Minus -> drawLine(
                    tint,
                    Offset(6f, 12f),
                    Offset(18f, 12f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )

                SloganGlyph.Trash -> {
                    drawLine(tint, Offset(5f, 7f), Offset(19f, 7f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(10f, 7f), Offset(10f, 4.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(14f, 7f), Offset(14f, 4.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(10f, 4.5f), Offset(14f, 4.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    val body = Path().apply {
                        moveTo(7f, 7f)
                        lineTo(8.2f, 20f)
                        lineTo(15.8f, 20f)
                        lineTo(17f, 7f)
                    }
                    drawPath(body, color = tint, style = Stroke(width = 1.8f, join = StrokeJoin.Round))
                    drawLine(tint, Offset(10.5f, 10f), Offset(10.8f, 17f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(13.5f, 10f), Offset(13.2f, 17f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                }

                SloganGlyph.Tune -> {
                    drawLine(tint, Offset(4f, 7f), Offset(20f, 7f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(4f, 12f), Offset(20f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(4f, 17f), Offset(20f, 17f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawCircle(tint, radius = 2.6f, center = Offset(9f, 7f))
                    drawCircle(tint, radius = 2.6f, center = Offset(15f, 12f))
                    drawCircle(tint, radius = 2.6f, center = Offset(8f, 17f))
                }

                SloganGlyph.Info -> {
                    drawCircle(tint, radius = 8f, center = Offset(12f, 12f), style = Stroke(width = 1.8f))
                    drawCircle(tint, radius = 1.2f, center = Offset(12f, 8.2f))
                    drawLine(tint, Offset(12f, 11f), Offset(12f, 16.2f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                }

                SloganGlyph.Volume -> {
                    val speaker = Path().apply {
                        moveTo(4f, 10f)
                        lineTo(8f, 10f)
                        lineTo(12f, 6f)
                        lineTo(12f, 18f)
                        lineTo(8f, 14f)
                        lineTo(4f, 14f)
                        close()
                    }
                    drawPath(speaker, color = tint)
                    drawArc(
                        color = tint,
                        startAngle = -55f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(10f, 8f),
                        size = Size(8f, 8f),
                        style = Stroke(width = 1.8f, cap = StrokeCap.Round),
                    )
                }

                SloganGlyph.Check -> {
                    val path = Path().apply {
                        moveTo(5f, 12.5f)
                        lineTo(10f, 17.5f)
                        lineTo(19f, 7f)
                    }
                    drawPath(
                        path,
                        color = tint,
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                SloganGlyph.ChevronRight -> {
                    val path = Path().apply {
                        moveTo(10f, 6f)
                        lineTo(16f, 12f)
                        lineTo(10f, 18f)
                    }
                    drawPath(
                        path,
                        color = tint,
                        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                SloganGlyph.Save -> {
                    val path = Path().apply {
                        moveTo(7f, 4f)
                        lineTo(17f, 4f)
                        lineTo(17f, 20f)
                        lineTo(12f, 16f)
                        lineTo(7f, 20f)
                        close()
                    }
                    drawPath(path, color = tint, style = Stroke(width = 1.8f, join = StrokeJoin.Round))
                }

                SloganGlyph.Megaphone -> {
                    val horn = Path().apply {
                        moveTo(4f, 10f)
                        lineTo(14f, 5f)
                        lineTo(14f, 19f)
                        lineTo(4f, 14f)
                        close()
                    }
                    drawPath(horn, color = tint)
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(6f, 14f),
                        size = Size(4f, 7f),
                        cornerRadius = CornerRadius(1.5f),
                    )
                    drawArc(
                        color = tint,
                        startAngle = -55f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(13f, 7f),
                        size = Size(9f, 10f),
                        style = Stroke(width = 1.8f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

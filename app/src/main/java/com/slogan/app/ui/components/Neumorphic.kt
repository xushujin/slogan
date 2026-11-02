package com.slogan.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.slogan.app.ui.theme.LocalSloganPalette
import com.slogan.app.ui.theme.SloganPalette

/**
 * 拟态表面的核心绘制逻辑：双向柔和阴影 + 同色系渐变。
 *
 * 阴影不用模糊滤镜，而是用 10 层逐渐偏移、逐渐变淡的同形状叠加模拟，
 * 这样在任何 API 级别、任何硬件加速状态下表现一致，也不依赖第三方库。
 */
fun Modifier.neumorphic(
    palette: SloganPalette,
    shape: Shape = RoundedCornerShape(22.dp),
    elevation: Dp = 8.dp,
    pressed: Boolean = false,
    fill: Color = palette.surface,
): Modifier = this
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val distance = elevation.toPx()
        if (distance > 0f && !pressed) {
            softShadow(
                outline = outline,
                color = palette.shadowDark,
                offsetX = distance * 0.55f,
                offsetY = distance * 0.55f,
                strength = 0.55f,
            )
            softShadow(
                outline = outline,
                color = palette.shadowLight,
                offsetX = -distance * 0.55f,
                offsetY = -distance * 0.55f,
                strength = 0.65f,
            )
        }

        val top = lerp(fill, palette.shadowLight, 0.12f)
        val bottom = lerp(fill, palette.shadowDark, 0.10f)
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                colors = listOf(top, fill, bottom),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )

        if (pressed) {
            drawOutline(
                outline = outline,
                brush = Brush.linearGradient(
                    0f to palette.shadowDark.copy(alpha = 0.38f),
                    0.5f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.9f, size.height * 0.9f),
                ),
            )
            drawOutline(
                outline = outline,
                brush = Brush.linearGradient(
                    0.5f to Color.Transparent,
                    1f to palette.shadowLight.copy(alpha = 0.30f),
                    start = Offset(size.width * 0.1f, size.height * 0.1f),
                    end = Offset(size.width, size.height),
                ),
            )
        }
    }
    .clip(shape)

private fun DrawScope.softShadow(
    outline: Outline,
    color: Color,
    offsetX: Float,
    offsetY: Float,
    strength: Float,
) {
    for (step in SHADOW_STEPS downTo 1) {
        val fraction = step / SHADOW_STEPS.toFloat()
        val alpha = strength * (1f - fraction) * (1f - fraction)
        translate(left = offsetX * fraction, top = offsetY * fraction) {
            drawOutline(outline = outline, color = color.copy(alpha = alpha))
        }
    }
}

private const val SHADOW_STEPS = 10

@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    elevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSloganPalette.current
    Column(
        modifier = modifier
            .neumorphic(palette = palette, shape = shape, elevation = elevation)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun NeumorphicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalSloganPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (accent) palette.accent else palette.surface
    val contentColor = when {
        !enabled -> palette.textSecondary.copy(alpha = 0.5f)
        accent -> palette.onAccent
        else -> palette.textPrimary
    }

    Row(
        modifier = modifier
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(50),
                elevation = if (accent) 10.dp else 7.dp,
                pressed = pressed,
                fill = fill,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun NeumorphicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = LocalSloganPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .neumorphic(
                palette = palette,
                shape = CircleShape,
                elevation = 6.dp,
                pressed = pressed,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.4f),
        ) {
            content()
        }
    }
}

@Composable
fun NeumorphicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSloganPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(50),
                elevation = if (selected) 4.dp else 6.dp,
                pressed = pressed || selected,
                fill = if (selected) palette.accent.copy(alpha = 0.18f) else palette.surface,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            color = if (selected) palette.accent else palette.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSloganPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "switchThumb",
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(50),
                elevation = 0.dp,
                pressed = true,
                fill = if (checked) palette.accent.copy(alpha = 0.22f) else palette.surface,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(26.dp)
                .neumorphic(
                    palette = palette,
                    shape = CircleShape,
                    elevation = if (pressed) 2.dp else 5.dp,
                    fill = if (checked) palette.accent else palette.surface,
                ),
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSloganPalette.current
    Text(
        text = text,
        modifier = modifier,
        color = palette.textSecondary,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun NeumorphicDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSloganPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NeumorphicCard(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(22.dp),
            elevation = 14.dp,
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                NeumorphicButton(onClick = onDismiss) {
                    Text(text = "取消", style = MaterialTheme.typography.labelLarge)
                }
                NeumorphicButton(onClick = onConfirm, accent = true, enabled = confirmEnabled) {
                    Text(text = confirmText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    val palette = LocalSloganPalette.current
    Text(
        text = text,
        modifier = modifier,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

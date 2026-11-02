package com.slogan.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slogan.app.core.playback.PlaybackState
import com.slogan.app.domain.model.PlaybackSettings
import com.slogan.app.domain.model.Preset
import com.slogan.app.ui.SloganUiState
import com.slogan.app.ui.components.NeumorphicButton
import com.slogan.app.ui.components.NeumorphicCard
import com.slogan.app.ui.components.NeumorphicChip
import com.slogan.app.ui.components.LabeledSlider
import com.slogan.app.ui.components.NeumorphicStepper
import com.slogan.app.ui.components.NeumorphicTextField
import com.slogan.app.ui.components.SectionLabel
import com.slogan.app.ui.components.SloganGlyph
import com.slogan.app.ui.components.SloganIcon
import com.slogan.app.ui.components.neumorphic
import com.slogan.app.ui.theme.LocalSloganPalette
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: SloganUiState,
    onDraftChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSaveClick: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVoiceChange: (String?) -> Unit,
) {
    val palette = LocalSloganPalette.current
    val settings = state.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusPanel(state = state)

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = "播报内容")
                Text(
                    text = "${state.draft.length} / ${Preset.MAX_BODY_LENGTH}",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            NeumorphicTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "例如：前方施工，请绕行，谢谢配合。",
                maxLength = Preset.MAX_BODY_LENGTH,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                NeumorphicButton(onClick = onSaveClick, enabled = state.canSave) {
                    SloganIcon(glyph = SloganGlyph.Save, size = 16.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.selectedPresetId == null) "保存为记录" else "更新记录",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            LabeledSlider(
                label = "播放间隔",
                valueText = formatInterval(settings.intervalMs),
                value = settings.intervalMs.toFloat(),
                range = PlaybackSettings.MIN_INTERVAL_MS.toFloat()..PlaybackSettings.MAX_INTERVAL_MS.toFloat(),
                step = 250f,
                onValueChange = { onIntervalChange(it.roundToInt()) },
            )
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = "循环频次")
                NeumorphicChip(
                    text = "无限",
                    selected = settings.repeatCount <= 0,
                    onClick = {
                        onRepeatChange(
                            if (settings.repeatCount <= 0) PlaybackSettings.DEFAULT_REPEAT_COUNT else 0,
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val infinite = settings.repeatCount <= 0
            NeumorphicStepper(
                valueText = if (infinite) "∞" else "${settings.repeatCount} 次",
                onDecrease = { onRepeatChange((settings.repeatCount - 1).coerceAtLeast(1)) },
                onIncrease = { onRepeatChange((settings.repeatCount + 1).coerceAtMost(PlaybackSettings.MAX_REPEAT_COUNT)) },
                decreaseEnabled = !infinite && settings.repeatCount > 1,
                increaseEnabled = !infinite && settings.repeatCount < PlaybackSettings.MAX_REPEAT_COUNT,
            )
            Spacer(modifier = Modifier.height(18.dp))

            LabeledSlider(
                label = "播放音量",
                valueText = "${(settings.volume * 100).roundToInt()}%",
                value = settings.volume,
                range = 0f..1f,
                step = 0.05f,
                onValueChange = onVolumeChange,
            )
            Spacer(modifier = Modifier.height(18.dp))

            SectionLabel(text = "音色")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NeumorphicChip(
                    text = "系统默认",
                    selected = settings.voiceName == null,
                    onClick = { onVoiceChange(null) },
                )
                state.voices.forEach { voice ->
                    NeumorphicChip(
                        text = voice.label,
                        selected = settings.voiceName == voice.name,
                        onClick = { onVoiceChange(voice.name) },
                    )
                }
            }
            if (!state.ttsReady) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在读取系统语音引擎…",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (state.voices.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前引擎没有可选音色，将使用系统默认。",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        NeumorphicButton(
            onClick = if (state.isBusy) onStop else onStart,
            accent = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SloganIcon(
                glyph = if (state.isBusy) SloganGlyph.Stop else SloganGlyph.Play,
                size = 18.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (state.isBusy) "停止播报" else "开始播报",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Text(
            text = "提示：音量实时生效；间隔、频次、音色与语速/音调在下次开始播报时生效。后台循环依赖系统「文字转语音」引擎，最终响度还受媒体音量影响。",
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StatusPanel(state: SloganUiState) {
    val palette = LocalSloganPalette.current
    val playback = state.playback
    val busy = playback.isBusy

    val pulseTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "statusPulseScale",
    )

    val progress = when (playback) {
        is PlaybackState.Playing -> playback.progress
        is PlaybackState.Waiting -> 1f
        else -> 0f
    }

    NeumorphicCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        elevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(148.dp)) {
                val stroke = 6.dp.toPx()
                val radius = size.minDimension / 2f - stroke
                drawCircle(
                    color = palette.shadowDark.copy(alpha = 0.45f),
                    radius = radius,
                    style = Stroke(width = stroke),
                )
                if (progress > 0f) {
                    drawArc(
                        color = palette.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        if (busy) {
                            scaleX = pulse
                            scaleY = pulse
                        }
                    }
                    .neumorphic(
                        palette = palette,
                        shape = CircleShape,
                        elevation = 9.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SloganIcon(
                    glyph = SloganGlyph.Megaphone,
                    size = 46.dp,
                    tint = if (busy) palette.accent else palette.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = statusTitle(playback),
            modifier = Modifier.fillMaxWidth(),
            color = palette.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = statusDetail(playback),
            modifier = Modifier.fillMaxWidth(),
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (state.settings.boost) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "扩音增强已开启",
                    color = palette.accent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun statusTitle(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "待命"
    is PlaybackState.Preparing -> "正在合成语音…"
    is PlaybackState.Playing -> "正在播报"
    is PlaybackState.Waiting -> "间隔中"
    is PlaybackState.Failed -> "播报中断"
}

private fun statusDetail(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "输入文字，选择间隔与频次，开始循环扩音"
    is PlaybackState.Preparing -> state.title.ifBlank { "首次播报需要合成，请稍候" }
    is PlaybackState.Playing -> if (state.totalIterations <= 0) {
        "无限循环 · 第 ${state.iteration + 1} 轮"
    } else {
        "第 ${state.iteration + 1} / ${state.totalIterations} 轮"
    }

    is PlaybackState.Waiting -> "${formatInterval(state.gapMs.toInt())}后继续"
    is PlaybackState.Failed -> state.message
}

private fun formatInterval(milliseconds: Int): String = when {
    milliseconds <= 0 -> "无缝衔接"
    milliseconds % 1000 == 0 -> "${milliseconds / 1000} 秒"
    else -> String.format(java.util.Locale.US, "%.1f 秒", milliseconds / 1000.0)
}

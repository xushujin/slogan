package com.slogan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.slogan.app.domain.model.PlaybackSettings
import com.slogan.app.ui.SloganUiState
import com.slogan.app.ui.components.LabeledSlider
import com.slogan.app.ui.components.NeumorphicButton
import com.slogan.app.ui.components.NeumorphicCard
import com.slogan.app.ui.components.NeumorphicSwitch
import com.slogan.app.ui.components.SectionLabel
import com.slogan.app.ui.components.SloganGlyph
import com.slogan.app.ui.components.SloganIcon
import com.slogan.app.ui.components.neumorphic
import com.slogan.app.ui.formatBytes
import com.slogan.app.ui.formatMultiplier
import com.slogan.app.ui.theme.LocalSloganPalette
import com.slogan.app.ui.theme.SloganPalette
import com.slogan.app.ui.theme.SloganPalettes

@Composable
fun SettingsScreen(
    state: SloganUiState,
    onThemeChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onBoostChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onAboutClick: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
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
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "外观主题")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "选择后立即生效，并作用于全部页面",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(14.dp))
            SloganPalettes.All.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { candidate ->
                        ThemeSwatch(
                            palette = candidate,
                            selected = candidate.id == settings.themeId,
                            onClick = { onThemeChange(candidate.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "声音细节")
            Spacer(modifier = Modifier.height(12.dp))
            LabeledSlider(
                label = "语速",
                valueText = formatMultiplier(settings.speechRate),
                value = settings.speechRate,
                range = PlaybackSettings.MIN_SPEECH_RATE..PlaybackSettings.MAX_SPEECH_RATE,
                step = 0.05f,
                onValueChange = onSpeechRateChange,
            )
            Spacer(modifier = Modifier.height(14.dp))
            LabeledSlider(
                label = "音调",
                valueText = formatMultiplier(settings.pitch),
                value = settings.pitch,
                range = PlaybackSettings.MIN_PITCH..PlaybackSettings.MAX_PITCH,
                step = 0.05f,
                onValueChange = onPitchChange,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "扩音增强",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "峰值归一化 + 软限幅，语音更响且不破音（下次播报生效）",
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                NeumorphicSwitch(checked = settings.boost, onCheckedChange = onBoostChange)
            }
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "存储")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "语音合成缓存",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已占用 ${formatBytes(state.cacheBytes)}，同一段文字不会重复合成",
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                NeumorphicButton(onClick = onClearCache) {
                    Text(text = "清除", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        UpdateCard(
            state = state.update,
            currentVersion = state.currentVersion,
            onCheck = onCheckUpdate,
            onDownload = onDownloadUpdate,
            onCancel = onCancelUpdate,
            onInstall = onInstallUpdate,
        )

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interaction, indication = null, onClick = onAboutClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "关于 Slogan",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "版本、技术栈、支持范围与权限说明",
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                SloganIcon(
                    glyph = SloganGlyph.ChevronRight,
                    size = 18.dp,
                    tint = palette.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ThemeSwatch(
    palette: SloganPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = LocalSloganPalette.current
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .neumorphic(
                palette = current,
                shape = RoundedCornerShape(18.dp),
                elevation = if (selected) 3.dp else 6.dp,
                pressed = selected,
                fill = if (selected) current.accent.copy(alpha = 0.12f) else current.surface,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(palette.background, palette.surface),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = palette.displayName,
            color = if (selected) current.accent else current.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

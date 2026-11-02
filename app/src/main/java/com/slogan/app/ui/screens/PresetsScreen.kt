package com.slogan.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slogan.app.domain.model.Preset
import com.slogan.app.ui.SloganUiState
import com.slogan.app.ui.components.EmptyHint
import com.slogan.app.ui.components.NeumorphicButton
import com.slogan.app.ui.components.NeumorphicCard
import com.slogan.app.ui.components.NeumorphicDialog
import com.slogan.app.ui.components.NeumorphicIconButton
import com.slogan.app.ui.components.SloganGlyph
import com.slogan.app.ui.components.SloganIcon
import com.slogan.app.ui.formatInterval
import com.slogan.app.ui.theme.LocalSloganPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PresetsScreen(
    state: SloganUiState,
    onPlay: (Preset) -> Unit,
    onLoad: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
) {
    val palette = LocalSloganPalette.current
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }

    if (state.presets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(26.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SloganIcon(
                        glyph = SloganGlyph.Save,
                        size = 40.dp,
                        tint = palette.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                EmptyHint(
                    text = "还没有保存的记录。\n在「播报」页写好内容后，点「保存为记录」，就能在这里随时复用。",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.presets, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                active = state.selectedPresetId == preset.id,
                onPlay = { onPlay(preset) },
                onLoad = { onLoad(preset) },
                onDelete = { pendingDelete = preset },
            )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    pendingDelete?.let { preset ->
        NeumorphicDialog(
            title = "删除记录",
            confirmText = "删除",
            onConfirm = {
                onDelete(preset)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        ) {
            Text(
                text = "确定删除「${preset.title}」？删除后无法恢复。",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    active: Boolean,
    onPlay: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalSloganPalette.current
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatter.format(Date(preset.updatedAt)),
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (active) {
                Text(
                    text = "编辑中",
                    modifier = Modifier.padding(end = 10.dp),
                    color = palette.accent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            NeumorphicIconButton(onClick = onDelete, size = 38.dp) {
                SloganIcon(glyph = SloganGlyph.Trash, size = 16.dp, tint = palette.danger)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = preset.body,
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = metaText(preset),
            color = palette.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeumorphicButton(onClick = onLoad) {
                Text(text = "载入编辑", style = MaterialTheme.typography.labelLarge)
            }
            NeumorphicButton(onClick = onPlay, accent = true) {
                SloganIcon(glyph = SloganGlyph.Play, size = 14.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "播放", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun metaText(preset: Preset): String = buildString {
    append(formatInterval(preset.intervalMs))
    append(" · ")
    append(if (preset.repeatCount <= 0) "无限循环" else "${preset.repeatCount} 次")
    append(" · 音量 ")
    append((preset.volume * 100).roundToInt())
    append("%")
    if (preset.boost) append(" · 扩音增强")
}

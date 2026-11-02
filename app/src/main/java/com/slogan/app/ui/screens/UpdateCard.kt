package com.slogan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slogan.app.core.update.UpdateState
import com.slogan.app.ui.components.NeumorphicButton
import com.slogan.app.ui.components.NeumorphicCard
import com.slogan.app.ui.components.NeumorphicProgressBar
import com.slogan.app.ui.components.SectionLabel
import com.slogan.app.ui.formatBytes
import com.slogan.app.ui.theme.LocalSloganPalette
import kotlin.math.roundToInt

/**
 * 设置页的更新卡片。
 *
 * 每个状态只给一个主操作，避免「检查中还能点下载」这类自相矛盾的入口。
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSloganPalette.current
    NeumorphicCard(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = "应用更新")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "当前版本 $currentVersion",
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))

        when (state) {
            UpdateState.Idle -> StatusText("从 GitHub Releases 获取最新版本", palette.textSecondary)
            UpdateState.Checking -> StatusText("正在检查更新…", palette.textSecondary)
            is UpdateState.UpToDate -> StatusText("已是最新版本", palette.accent)

            is UpdateState.Available -> {
                StatusText(
                    "发现新版本 ${state.release.version}（${formatBytes(state.release.apk.sizeBytes)}）",
                    palette.accent,
                )
                if (state.release.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.release.notes,
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            is UpdateState.Downloading -> {
                val percent = (state.progress * 100f).roundToInt()
                StatusText("正在下载 ${state.release.version} · $percent%", palette.textSecondary)
                Spacer(modifier = Modifier.height(10.dp))
                NeumorphicProgressBar(progress = state.progress)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                    color = palette.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            is UpdateState.Ready -> StatusText("安装包已下载并通过校验", palette.accent)
            is UpdateState.Failed -> StatusText(state.message, palette.danger)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                UpdateState.Idle, is UpdateState.UpToDate -> ActionButton("检查更新", onCheck)

                UpdateState.Checking -> NeumorphicButton(onClick = {}, enabled = false) {
                    Text(text = "检查中…", style = MaterialTheme.typography.labelLarge)
                }

                is UpdateState.Available -> ActionButton("下载更新", onDownload, accent = true)

                is UpdateState.Downloading -> ActionButton("取消", onCancel)

                is UpdateState.Ready -> ActionButton("安装", onInstall, accent = true)

                is UpdateState.Failed -> {
                    if (state.release != null) {
                        ActionButton("重试下载", onDownload, accent = true)
                    }
                    ActionButton("重新检查", onCheck)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, accent: Boolean = false) {
    NeumorphicButton(onClick = onClick, accent = accent) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
}

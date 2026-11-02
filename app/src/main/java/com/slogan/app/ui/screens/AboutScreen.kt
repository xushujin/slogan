package com.slogan.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slogan.app.BuildConfig
import com.slogan.app.ui.components.NeumorphicButton
import com.slogan.app.ui.components.NeumorphicCard
import com.slogan.app.ui.components.SectionLabel
import com.slogan.app.ui.components.SloganGlyph
import com.slogan.app.ui.components.SloganIcon
import com.slogan.app.ui.components.neumorphic
import com.slogan.app.ui.theme.LocalSloganPalette

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val palette = LocalSloganPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeumorphicButton(onClick = onBack) {
                SloganIcon(glyph = SloganGlyph.ChevronRight, size = 14.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "返回", style = MaterialTheme.typography.labelLarge)
            }
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .neumorphic(palette = palette, shape = CircleShape, elevation = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SloganIcon(
                        glyph = SloganGlyph.Megaphone,
                        size = 38.dp,
                        tint = palette.accent,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Slogan",
                modifier = Modifier.fillMaxWidth(),
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                modifier = Modifier.fillMaxWidth(),
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "把文字变成可以反复播放的扩音播报：一次合成，循环播放，支持间隔、频次、音色与音量调节，并可保存多条记录复用。",
                modifier = Modifier.fillMaxWidth(),
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "技术栈")
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("语言", "Kotlin ${BuildConfig.KOTLIN_VERSION}")
            InfoRow("界面", "Jetpack Compose（BOM ${BuildConfig.COMPOSE_BOM_VERSION}）")
            InfoRow("语音合成", "系统 TextToSpeech · synthesizeToFile")
            InfoRow("音频处理", "自研 WAV 编解码 · 峰值归一化 / 软限幅")
            InfoRow("播放", "AudioTrack 单实例循环，无第三方播放器")
            InfoRow("存储", "原生 SQLite，显式版本迁移")
            InfoRow("更新", "GitHub Releases · 自研下载与签名校验")
            InfoRow("架构", "单 Activity · 手写依赖容器，无 DI 框架")
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "支持范围")
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("系统", "Android 8.0（API ${BuildConfig.MIN_SDK_INT}）及以上")
            InfoRow("适配", "targetSdk ${BuildConfig.TARGET_SDK_INT}")
            InfoRow("依赖", "系统需安装可用的「文字转语音」引擎")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "不同机型的扬声器功率、系统音效与 TTS 引擎音色存在差异，实际扩音效果以设备为准。",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "权限与可见性")
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow(
                title = "通知",
                detail = "Android 13 及以上用于展示循环播报的常驻通知；拒绝后仍可播放，但无法在通知栏停止。",
            )
            PermissionRow(
                title = "前台服务",
                detail = "用于锁屏、切后台后继续循环播放，通知栏会持续显示播放状态。",
            )
            PermissionRow(
                title = "前台服务（媒体播放）",
                detail = "Android 14 及以上声明服务类型，系统据此判断播放合法性。",
            )
            PermissionRow(
                title = "唤醒锁",
                detail = "仅在播放期间持有，防止息屏后 CPU 休眠导致循环中断。",
            )
            PermissionRow(
                title = "查询 TTS 引擎",
                detail = "Android 11 起需要声明包可见性，才能发现并调用系统语音引擎。",
            )
            PermissionRow(
                title = "网络",
                detail = "仅在你点击「检查更新」时访问 api.github.com 与 GitHub 下载地址，用于获取版本信息与安装包；应用不做任何统计上报。",
            )
            PermissionRow(
                title = "安装未知来源应用",
                detail = "下载完新版本后拉起系统安装器需要此授权。Android 8.0 起按应用授权，首次安装时系统会跳转到设置页由你确认。",
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "语音合成由系统语音引擎完成，不经过本应用的网络请求。应用不会在后台自动联网，只有点击「检查更新」才会发起请求。",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "构建信息")
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("包名", BuildConfig.APPLICATION_ID)
            InfoRow("构建类型", BuildConfig.BUILD_TYPE)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalSloganPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = palette.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PermissionRow(title: String, detail: String) {
    val palette = LocalSloganPalette.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            color = palette.textPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = detail,
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

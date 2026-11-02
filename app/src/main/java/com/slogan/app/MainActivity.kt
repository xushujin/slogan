package com.slogan.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slogan.app.core.playback.PlaybackState
import com.slogan.app.ui.SloganUiState
import com.slogan.app.ui.SloganViewModel
import com.slogan.app.ui.components.NeumorphicDialog
import com.slogan.app.ui.components.NeumorphicTextField
import com.slogan.app.ui.components.SloganGlyph
import com.slogan.app.ui.components.SloganIcon
import com.slogan.app.ui.components.neumorphic
import com.slogan.app.ui.screens.AboutScreen
import com.slogan.app.ui.screens.HomeScreen
import com.slogan.app.ui.screens.PresetsScreen
import com.slogan.app.ui.screens.SettingsScreen
import com.slogan.app.ui.theme.LocalSloganPalette
import com.slogan.app.ui.theme.SloganPalette
import com.slogan.app.ui.theme.SloganPalettes
import com.slogan.app.ui.theme.SloganTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SloganApp).container
        setContent {
            val viewModel: SloganViewModel = viewModel(factory = SloganViewModel.factory(container))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val palette = SloganPalettes.byId(state.settings.themeId)

            SloganTheme(palette = palette) {
                SystemBarsEffect(palette = palette)
                SloganRoot(state = state, viewModel = viewModel)
            }
        }
    }
}

private enum class RootTab(val label: String, val glyph: SloganGlyph) {
    Broadcast("播报", SloganGlyph.Megaphone),
    Presets("记录", SloganGlyph.Save),
    Settings("设置", SloganGlyph.Tune),
}

@Composable
private fun SloganRoot(state: SloganUiState, viewModel: SloganViewModel) {
    val palette = LocalSloganPalette.current
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(RootTab.Broadcast) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = showAbout) { showAbout = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (!showAbout) {
                TopBar(state = state)
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    showAbout -> AboutScreen(onBack = { showAbout = false })

                    else -> when (tab) {
                        RootTab.Broadcast -> HomeScreen(
                            state = state,
                            onDraftChange = viewModel::onDraftChange,
                            onStart = {
                                requestNotificationPermission()
                                viewModel.startPlayback()
                            },
                            onStop = viewModel::stopPlayback,
                            onSaveClick = { showSaveDialog = true },
                            onIntervalChange = viewModel::setInterval,
                            onRepeatChange = viewModel::setRepeatCount,
                            onVolumeChange = viewModel::setVolume,
                            onVoiceChange = viewModel::setVoice,
                        )

                        RootTab.Presets -> PresetsScreen(
                            state = state,
                            onPlay = viewModel::playPreset,
                            onLoad = { preset ->
                                viewModel.loadPreset(preset)
                                // 载入后直接回到播报页，否则用户看不到内容已被替换。
                                tab = RootTab.Broadcast
                            },
                            onDelete = viewModel::deletePreset,
                        )

                        RootTab.Settings -> SettingsScreen(
                            state = state,
                            onThemeChange = viewModel::setTheme,
                            onSpeechRateChange = viewModel::setSpeechRate,
                            onPitchChange = viewModel::setPitch,
                            onBoostChange = viewModel::setBoost,
                            onClearCache = viewModel::clearCache,
                            onAboutClick = { showAbout = true },
                            onCheckUpdate = viewModel::checkForUpdate,
                            onDownloadUpdate = viewModel::downloadUpdate,
                            onCancelUpdate = viewModel::cancelUpdateDownload,
                            onInstallUpdate = viewModel::installUpdate,
                        )
                    }
                }
            }
            if (!showAbout) {
                BottomBar(selected = tab, onSelect = { tab = it })
            }
        }

        val message = state.message
        if (message != null) {
            LaunchedEffect(message) {
                delay(2_200)
                viewModel.consumeMessage()
            }
            MessageBar(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 104.dp),
            )
        }
    }

    if (showSaveDialog) {
        var title by remember { mutableStateOf(state.draft.take(12)) }
        NeumorphicDialog(
            title = "保存记录",
            confirmText = "保存",
            onConfirm = {
                viewModel.savePreset(title)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
            confirmEnabled = state.draft.isNotBlank(),
        ) {
            Text(
                text = "给这条播报起个名字，之后可以在「记录」里一键复用。",
                color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            NeumorphicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "记录名称",
                minLines = 1,
                maxLines = 1,
                maxLength = 40,
            )
        }
    }
}

@Composable
private fun TopBar(state: SloganUiState) {
    val palette = LocalSloganPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Slogan",
                color = palette.textPrimary,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 26.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            Text(
                text = "循环扩音 · 文字转语音",
                color = palette.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        StatusPill(playback = state.playback)
    }
}

@Composable
private fun StatusPill(playback: PlaybackState) {
    val palette = LocalSloganPalette.current
    val (label, color) = when (playback) {
        PlaybackState.Idle -> "待命" to palette.textSecondary
        is PlaybackState.Preparing -> "合成中" to palette.accent
        is PlaybackState.Playing -> "播放中" to palette.accent
        is PlaybackState.Waiting -> "间隔" to palette.accent
        is PlaybackState.Failed -> "异常" to palette.danger
    }
    Row(
        modifier = Modifier
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(50),
                elevation = 4.dp,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RootTab.entries.forEach { tab ->
            TabButton(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabButton(
    tab: RootTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSloganPalette.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(18.dp),
                elevation = if (selected) 2.dp else 6.dp,
                pressed = selected,
                fill = if (selected) palette.accent.copy(alpha = 0.14f) else palette.surface,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SloganIcon(
            glyph = tab.glyph,
            size = 18.dp,
            tint = if (selected) palette.accent else palette.textSecondary,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = tab.label,
            color = if (selected) palette.accent else palette.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MessageBar(message: String, modifier: Modifier = Modifier) {
    val palette = LocalSloganPalette.current
    Box(
        modifier = modifier
            .neumorphic(
                palette = palette,
                shape = RoundedCornerShape(50),
                elevation = 10.dp,
                fill = palette.accent,
            )
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text = message,
            color = palette.onAccent,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SystemBarsEffect(palette: SloganPalette) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return
    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !palette.dark
        controller.isAppearanceLightNavigationBars = !palette.dark
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

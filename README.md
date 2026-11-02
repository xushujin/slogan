# Slogan

一个用 Kotlin + Jetpack Compose 写的原生 Android 扩音器应用：把文字转成语音，按设定的间隔与频次循环播放，适合摆摊、现场引导、集会喊话等需要反复播报的场景。

- 应用名：**Slogan**
- 包名：`com.slogan.app`
- 最低系统：Android 8.0（API 26）
- 目标系统：Android 16（API 36）

---

## 1. 功能

| 能力 | 说明 |
| --- | --- |
| 文字转语音 | 调用系统 `TextToSpeech` 引擎，支持中文及引擎提供的其它语言 |
| 间隔可调 | 0–10 秒，0 表示无缝衔接 |
| 频次可调 | 1–999 次，或无限循环 |
| 音色可调 | 系统引擎提供的 Voice 列表 + 语速 + 音调 |
| 音量可调 | 播放中实时生效，不需要重新合成 |
| 扩音增强 | 峰值归一化 + 软限幅，提升平均响度且不破音 |
| 记录复用 | 保存多条播报记录，支持载入编辑、直接播放、删除 |
| 后台播放 | 前台服务 + 常驻通知 + 唤醒锁 + 音频焦点 |
| 多套主题 | 石墨 / 靛夜 / 深海 / 苔绿 / 暖砂 / 樱粉，切换全局生效 |
| 关于页 | 应用名、版本号、技术栈、支持范围、权限说明 |

### 关键设计约束的落实方式

| 需求 | 实现 |
| --- | --- |
| **不能每轮循环都重新合成** | `SynthesisCache` 以 `SHA-256(文本 + 音色 + 语速 + 音调 + 引擎)` 为键缓存 WAV；`PlaybackEngine` 在循环开始前只取一次文件并解码为内存 PCM，循环只读内存 |
| **正确处理后台播放** | `PlaybackService` 以 `mediaPlayback` 类型前台运行；通知常驻并可停止；播放期间持有 `PARTIAL_WAKE_LOCK`（10 分钟超时，每轮续期）；音频焦点丢失时暂停/停止 |
| **避免循环重叠** | 进程内只有一个 `PlaybackEngine` 和一个 `PcmPlayer`；会话通过 `Mutex` 串行，新会话启动前 `cancelAndJoin` 旧会话；整个会话只创建一个 `AudioTrack` |
| **实际扩音效果** | 去直流 → 峰值归一化（-1 dBFS，扩音模式 -0.09 dBFS）→ `tanh` 软限幅 → 首尾淡入淡出；避免削顶失真和循环接缝爆音 |
| **同一设置只有一套实现** | 设置只存在 `settings` 表，经 `SettingsStore` 暴露为唯一 `StateFlow`；UI 与播放引擎都读这一份，不存在第二套偏好存储 |
| **schema 变更显式迁移** | `SloganSchema` 集中描述 v1 建表、v1→v2 迁移与最新建表；`onUpgrade` 缺迁移直接抛异常，`onDowngrade` 拒绝启动，无 destructive migration |

---

## 2. 技术栈与版本

所有版本集中在 `gradle/libs.versions.toml`，`关于页` 直接读取 `BuildConfig`，不会与代码漂移。

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Kotlin | 2.2.21 | 语言与 Compose 编译器插件同版本 |
| Android Gradle Plugin | 8.13.2 | 支持 compileSdk 36 |
| Gradle | 8.14.3 | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 21 | 源码/字节码目标为 Java 17 |
| Compose BOM | 2025.10.00 | Material3 1.4.0 |
| compileSdk / targetSdk | 36 / 36 | Android 16 |
| minSdk | 26 | Android 8.0 |
| Build Tools | 35.0.0 | AGP 默认版本 |
| AndroidX Core KTX | 1.17.0 | |
| Activity Compose | 1.11.0 | |
| Lifecycle | 2.9.4 | |
| 测试 | JUnit 4.13.2 + sqlite-jdbc 3.54.1.0（仅测试） | |

### 为什么不是更高的 Compose BOM

本机验证过 Compose BOM `2026.09.00`：其 `animation-core` 等 AAR 要求 `compileSdk 37`，而 AGP 8.13.2 支持的最高 compileSdk 是 36，直接构建会失败：

```
Dependency 'androidx.compose.animation:animation-core-android:1.12.1' requires
libraries and applications that depend on it to compile against version 37 or later.
```

因此这里选用当前工具链可用的最新稳定组合（BOM 2025.10.00 / Material3 1.4.0）。升级到 BOM 2026.09 需要同时升级到 AGP 9.x + compileSdk 37，属于独立的重构任务，不在本次范围内。

### 依赖边界

- 运行时依赖只有 AndroidX 官方库（Compose、Core、Lifecycle），**没有**第三方 UI 库、图片库、播放器、DI 框架。
- WAV 解析/写入、响度处理、AudioTrack 播放循环、拟态绘制、图标、SQLite 访问全部自己实现。
- `sqlite-jdbc` 只用于 JVM 单元测试里执行真实迁移 SQL，不会进入 APK。

---

## 3. 目录结构

```
.
├── .github/workflows/
│   ├── ci.yml                 # push/PR：单测 + Lint + Debug 构建
│   └── release.yml            # push tag v*：签名构建 + 校验 + 发布 Release
├── app/
│   ├── build.gradle.kts       # 签名双通道、版本注入、零警告配置
│   ├── lint.xml               # 仅忽略自适应图标目录的 ObsoleteSdkInt
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/java/com/slogan/app/
│       │   ├── SloganApp.kt           # Application + 手写依赖容器
│       │   ├── MainActivity.kt        # 单 Activity、底部三栏、关于页入口
│       │   ├── core/audio/            # WavCodec / PcmAudio / LoudnessProcessor
│       │   ├── core/playback/         # PlaybackEngine / PcmPlayer / 时间轴 / 音频焦点
│       │   ├── core/tts/              # SpeechSynthesizer / SynthesisCache / 缓存键
│       │   ├── data/                  # SloganSchema / SloganDatabase / DAO / SettingsStore
│       │   ├── domain/model/          # Preset / PlaybackSettings
│       │   ├── playback/              # PlaybackService（前台服务）
│       │   └── ui/                    # 主题、拟态组件、四个页面、ViewModel
│       ├── main/res/                  # 图标、通知图标、主题、备份规则
│       └── test/                      # 33 个 JVM 单测 + 真实 WAV 资源
├── gradle/libs.versions.toml
└── README.md
```

---

## 4. 构建

### 4.1 环境

- JDK 17 及以上（CI 与本文验证使用 JDK 21）
- Android SDK：platform 36、build-tools 35.0.0
- 在 `local.properties` 写入 `sdk.dir=/path/to/Android/sdk`，或设置 `ANDROID_HOME`

### 4.2 常用命令

```bash
# Debug
./gradlew :app:assembleDebug

# 单元测试（33 个）
./gradlew :app:testDebugUnitTest

# Lint（warningsAsErrors = true）
./gradlew :app:lintDebug

# Release（未配置签名时产出 app-release-unsigned.apk）
./gradlew :app:assembleRelease -PversionName=1.0.0 -PversionCode=1
```

产物路径：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

### 4.3 零警告策略

| 位置 | 配置 |
| --- | --- |
| Kotlin | `compilerOptions.allWarningsAsErrors = true` |
| Lint | `abortOnError = true`、`warningsAsErrors = true` |
| 依赖升级提示 | 显式关闭 `GradleDependency`、`AndroidGradlePluginVersion`、`NewerVersionAvailable`（与代码质量无关） |

验证结果见第 8 节。

---

## 5. 签名：两条通道

Gradle 在配置阶段按以下优先级解析签名材料：

1. 环境变量（CI 通道）
2. 仓库根目录 `keystore.properties`（本地通道）
3. 都没有 → release 不签名（不会偷偷用 debug 签名）

`keystore.properties` 已在 `.gitignore` 中，禁止提交。

### 5.1 本地通道

复制仓库根目录的 `keystore.properties.example` 为 `keystore.properties`，填入真实值：

```properties
# keystore.properties（仓库根目录，不提交）
storeFile=/absolute/path/to/slogan-release.jks
storePassword=你的 storePassword
keyAlias=slogan
keyPassword=你的 keyPassword
```

`storeFile` 相对路径以仓库根目录为基准，也支持绝对路径。

生成 keystore：

```bash
keytool -genkeypair -v \
  -keystore slogan-release.jks \
  -alias slogan \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <storePassword> -keypass <keyPassword> \
  -dname "CN=Slogan, OU=Mobile, O=Slogan, L=Shanghai, ST=Shanghai, C=CN"
```

> 这个 keystore 一旦用于正式发布就必须长期保存；丢失后无法对已安装用户升级同一应用。

### 5.2 CI 通道

`release.yml` 把 4 个 Secret 映射为环境变量：

| 环境变量 | 来源 Secret |
| --- | --- |
| `SLOGAN_KEYSTORE_PATH` | 由 `KEYSTORE_BASE64` 解码到 `$RUNNER_TEMP/release.jks` |
| `SLOGAN_KEYSTORE_PASSWORD` | `KEYSTORE_PASSWORD` |
| `SLOGAN_KEY_ALIAS` | `KEY_ALIAS` |
| `SLOGAN_KEY_PASSWORD` | `KEY_PASSWORD` |

签名开关只有 `signingMaterial != null` 时才创建 `release` signingConfig；缺失时构建产物为 unsigned，而 `release.yml` 会用 `apksigner verify` 强制校验，签名缺失会直接失败。

签名方案：minSdk 26，使用 v2 + v3；v1（JAR 签名）显式关闭。

---

## 6. 发版流程（GitHub Flow）

```
main ──●────────●────────────●────────●──▶
        \      /              \      /
         ●────●  feat/xxx      ●────●  fix/xxx
                                   │
                              tag v1.2.3
                                   │
                          Release workflow
```

1. 从 `main` 切功能分支（`feat/`、`fix/`、`chore/`）。
2. 提交 PR 到 `main`，CI 自动跑单测、Lint、Debug 构建。
3. Review 合并后，在 `main` 上打标签并推送：
   ```bash
   git tag -a v1.2.3 -m "Slogan 1.2.3"
   git push origin v1.2.3
   ```
4. `release.yml` 触发：
   - 校验标签格式（`v1.2.3` 或 `v1.2.3-rc1`）
   - `versionName = 标签去掉 v`，`versionCode = github.run_number`
   - 解码 keystore → 签名构建 → `apksigner verify` → 校验 mapping
   - 生成 `Slogan-<version>.apk`、`.sha256`、`mapping-<version>.txt`
   - `gh release create` 自动发布 Release

版本号规则：

- `versionName` 来自标签，例如 `v1.2.3` → `1.2.3`
- `versionCode` 来自 `github.run_number`，单调递增；本地构建默认 `1`
- 也可手动覆盖：`./gradlew assembleRelease -PversionName=1.2.3 -PversionCode=42`

---

## 7. GitHub 仓库需要配置的参数

进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，添加以下 4 个 Secret（不需要配置 Variables）：

| 名称 | 类型 | 值 |
| --- | --- | --- |
| `KEYSTORE_BASE64` | Secret | 正式 keystore 的 Base64 单行文本，见下方命令 |
| `KEYSTORE_PASSWORD` | Secret | keystore 的 `storePassword` |
| `KEY_ALIAS` | Secret | key alias，例如 `slogan` |
| `KEY_PASSWORD` | Secret | key 的 `keyPassword` |

生成 `KEYSTORE_BASE64`：

```bash
# macOS
base64 -i slogan-release.jks | tr -d '\n' | pbcopy

# Linux
base64 -w0 slogan-release.jks
```

还需要：

1. **Settings → Actions → General → Workflow permissions** 选择 **Read and write permissions**（`gh release create` 需要写权限；`release.yml` 也声明了 `permissions: contents: write`）。
2. 建议保护 `main` 分支：要求 PR + CI 通过后再合并。
3. 建议保护 `v*` 标签，避免误删/误推。

> 如果 keystore 换了，必须同时更新 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 四项。

---

## 8. 测试与验证记录

以下均为本仓库实际执行过的命令与结果。

### 8.1 编译与静态检查

```bash
./gradlew :app:compileDebugKotlin   # BUILD SUCCESSFUL（allWarningsAsErrors=true）
./gradlew :app:lintDebug            # BUILD SUCCESSFUL（warningsAsErrors=true）
./gradlew :app:assembleDebug        # BUILD SUCCESSFUL
./gradlew :app:assembleRelease      # BUILD SUCCESSFUL（R8 + resource shrink）
```

Lint 首轮发现 6 个 error（`UnusedAttribute`、`DataExtractionRules`、`ObsoleteSdkInt`、`MonochromeLauncherIcon` ×2、`UseKtx`），已逐项修复；`ObsoleteSdkInt` 因自适应图标必须放在 `mipmap-anydpi-v26` 而在 `app/lint.xml` 中定点忽略。

### 8.2 单元测试（33 个，全部通过）

| 测试类 | 数量 | 覆盖内容 |
| --- | --- | --- |
| `WavCodecTest` | 9 | 真实 ffmpeg 文件、LIST 块、奇数块填充、WAVE_FORMAT_EXTENSIBLE、8 bit/float 拒绝、data 长度非法、编解码往返 |
| `LoudnessProcessorTest` | 6 | 归一化目标、软限幅不削顶、首尾淡入淡出、静音、立体声布局、参数校验 |
| `PlaybackTimelineTest` | 5 | 总时长、最后一轮无间隔、单次播放、无限循环、零间隔 |
| `SynthesisKeyTest` | 6 | 键稳定性、文本/音色/语速/音调/引擎变化必须换键 |
| `DatabaseMigrationTest` | 7 | v1 数据保留、迁移后 schema 与全新安装一致、默认值、非破坏性、拒绝降级、缺迁移报错 |

### 8.3 真实文件验证（WAV）

测试资源由 ffmpeg 生成，不是手工构造的“看起来像 WAV”的字节：

```bash
ffmpeg -f lavfi -i "sine=frequency=440:duration=1" -ac 2 -ar 44100 \
  -c:a pcm_s16le -metadata title="Slogan Fixture" app/src/test/resources/wav/tone_stereo_44100.wav
ffprobe -show_entries stream=sample_rate,channels,bits_per_sample,duration ...
# sample_rate=44100 channels=2 bits_per_sample=16 duration=1.000000
ffmpeg -i tone_stereo_44100.wav -af volumedetect -f null -
# max_volume: -21.1 dB
```

测试断言解码峰值落在 `2800..3000`（对应 -21.1 dBFS），并统计 440 Hz 在 1 秒内的过零次数在 `860..900`，以此证明采样顺序与字节序正确；文件中的 `LIST/INFO` 块与长度为 7 的 `IART` 块同时覆盖了真实文件的块遍历与奇数对齐。

### 8.4 APK 二进制验证

```bash
apksigner verify --verbose --print-certs app-release.apk
# Verified using v2 scheme: true
# Verified using v3 scheme: true
# Signer #1 certificate DN: CN=Slogan Test, OU=QA, O=Slogan, ...

aapt2 dump badging app-release.apk
# package: name='com.slogan.app' versionCode='42' versionName='1.2.3'
# minSdkVersion:'26'  targetSdkVersion:'36'

aapt2 dump xmltree --file AndroidManifest.xml app-release.apk
# activity: com.slogan.app.MainActivity (exported=true)
# service:  com.slogan.app.playback.PlaybackService
#           exported=false, foregroundServiceType=0x00000002 (mediaPlayback)
```

同时确认 `BuildConfig` 的版本常量已进入 dex：`strings classes.dex` 可看到 `1.2.3`、`BOM 2025.10.00`、`Kotlin 2.2.21`。

### 8.5 未验证项（明确标注）

| 项目 | 状态 |
| --- | --- |
| 真机/模拟器运行 | **未执行**。本机没有安装 Android Emulator 与系统镜像，也没有连接设备；UI 交互、TTS 实际发声、AudioTrack 循环、前台服务保活均未做端到端运行验证 |
| CI 环境变量签名通道 | **未在本机执行**。本地只验证了 `keystore.properties` 通道；CI 通道代码路径相同，且 workflow 内强制 `apksigner verify`，首次打 tag 时会得到真实验证 |
| 不同 TTS 引擎的 WAV 输出 | 仅支持 16 bit 线性 PCM WAV；若某引擎输出 float/8 bit/非 WAV，会抛出明确错误而不是静默播放失败 |
| 实际扩音响度 | 取决于设备扬声器功率、系统音效与媒体音量，代码只保证不削顶、平均响度尽量高 |
| 音频输出路由 | 走系统媒体通道；连接耳机/蓝牙时会从对应设备播放，不会强制切回扬声器（强制切换需要 `MODE_IN_COMMUNICATION`，会显著改变音质且难以在无设备环境下验证） |

---

## 9. 权限与支持范围

| 权限 | 用途 |
| --- | --- |
| `FOREGROUND_SERVICE` | 后台循环播放 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ 声明前台服务类型 |
| `POST_NOTIFICATIONS` | Android 13+ 展示常驻播放通知（拒绝仍可播放，但通知栏不可见） |
| `WAKE_LOCK` | 播放期间防止息屏休眠中断循环 |

不申请 `INTERNET`：语音合成由系统 TTS 引擎负责，本应用只写本地文件、读本地文件、驱动 AudioTrack。

支持范围：Android 8.0（API 26）至 Android 16（API 36）。需要系统安装可用的「文字转语音」引擎。

---

## 10. 需求 → 代码对照

| 需求 | 代码位置 |
| --- | --- |
| 文字转语音 | `core/tts/SpeechSynthesizer.kt` |
| 一次合成、循环复用 | `core/tts/SynthesisCache.kt`、`core/playback/PlaybackEngine.kt` |
| 间隔 / 频次 | `domain/model/PlaybackSettings.kt`、`core/playback/PlaybackTimeline.kt` |
| 音色 / 语速 / 音调 | `core/tts/SpeechSynthesizer.kt`、`ui/screens/SettingsScreen.kt` |
| 音量实时调节 | `core/playback/PcmPlayer.kt`、`PlaybackEngine.updateVolume` |
| 扩音效果 | `core/audio/LoudnessProcessor.kt` |
| 防重叠 | `PlaybackEngine`（Mutex + cancelAndJoin）、`PcmPlayer`（单 AudioTrack） |
| 后台播放 | `playback/PlaybackService.kt` |
| 记录保存 | `data/PresetDao.kt`、`ui/screens/PresetsScreen.kt` |
| 显式迁移 | `data/SloganSchema.kt`、`data/SloganDatabase.kt` |
| 拟态 UI | `ui/components/Neumorphic.kt`、`ui/components/Inputs.kt`、`ui/components/SloganIcon.kt` |
| 多主题 | `ui/theme/SloganPalette.kt`、`ui/theme/SloganTheme.kt` |
| 关于页 | `ui/screens/AboutScreen.kt` |
| 签名双通道 | `app/build.gradle.kts`、`.github/workflows/release.yml` |
| 零警告 | `app/build.gradle.kts`、`app/lint.xml` |

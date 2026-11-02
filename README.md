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
| 应用内更新 | 从 GitHub Releases 检查新版本，带下载进度、SHA-256 校验与签名比对，可直接拉起系统安装器 |
| 关于页 | 应用名、版本号、技术栈、支持范围、权限说明、上次崩溃记录 |

### 应用内更新

更新源是 GitHub Releases，不依赖任何第三方更新 SDK。

| 环节 | 实现 |
| --- | --- |
| 检查 | `GET https://api.github.com/repos/xushujin/slogan/releases/latest`，用 `tag_name` 与 `BuildConfig.VERSION_NAME` 做语义化版本比较 |
| 下载 | `HttpURLConnection` 流式写入 `cacheDir/updates/*.part`，按 80ms 节流上报进度，完成后改名为正式文件 |
| 校验 | 优先用资产自带的 `digest`（`sha256:...`），没有才下载同名 `.sha256` 附件；再比对安装包与当前应用的包名、签名 |
| 安装 | `FileProvider` 授予读权限，`ACTION_VIEW` 拉起系统安装器；未授权时跳转「安装未知应用」设置页 |

状态机 `UpdateState` 共七种：`Idle / Checking / UpToDate / Available / Downloading / Ready / Failed`。
每个状态只给一个主操作，不存在「检查中还能点下载」这类自相矛盾的入口；取消下载会退回
`Available`，可以直接重试而不必重新检查。

三点值得单独说明：

1. **签名比对是真正的安全边界。** 校验和只能发现下载损坏——它和 APK 来自同一个源，
   挡不住有人替换发布内容。真正拦住「同包名但非本应用签名的 APK」的是
   `getPackageArchiveInfo` 取出的签名与已安装应用签名求交集，为空就拒绝。
2. **关闭 gzip。** 请求带 `Accept-Encoding: identity`，否则 `Content-Length` 是压缩后长度、
   读到的却是解压后字节，进度条会超过 100%。
3. **不做后台自动检查。** 只有用户点击「检查更新」才会联网，关于页的权限说明据此撰写。

已知限制：debug 构建的包名是 `com.slogan.app.debug`、签名也是 debug key，
因此**调试版无法通过应用内更新安装正式版**，校验会明确提示包名或签名不一致。

### 音色与语言匹配

系统 TTS 的音色由各家引擎自行注册，行为差异极大。真机上遇到过这些问题，
所以这一块的实现原则是「不假设引擎是规范的」：

| 处理 | 说明 |
| --- | --- |
| 「系统默认」 | 不设置 voice，只确保引擎切到中文。**绝不**用 `setVoice(null)` 表达——平台实现会对 null 调 `voice.getName()` 直接 NPE |
| 切回系统默认 | `mParams` 里的 voice 一旦写入没有公开 API 能清除，因此必须**重建引擎**，否则会一直念上一个音色 |
| 系统默认不可用 | 部分引擎的默认音色无法合成，失败后自动退到引擎第一个音色重试一次，而不是让用户卡死 |
| `setVoice` 返回值 | 不忽略。切换失败明确报错，而不是静默沿用上一个音色 |
| 音色列表 | 需联网的音色标注「（需联网）」 |

两个曾经踩过的坑：

1. **`setVoice` 返回值被 `runCatching` 吞掉**——引擎拒绝切换时安静回退到默认音色，
   界面却显示已选中，表现为「换了音色但声音没变」。
2. **不能用 `Voice.locale` 判断是不是中文音色**。实测某厂商引擎把中文报成 `chn`
   （标准里是奇努克混合语）、英文报成 `usa`，`toLanguageTag()` 直接输出这些非法值。
   按 locale 判断会 100% 误判，所以现在只在「合成失败」时才给语言相关的提示，
   不做事前拦截。排序上把 `zh/chn/chi/zho/cmn` 都当中文处理，纯粹为了让中文音色排在前面。


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
- WAV 解析/写入、响度处理、AudioTrack 播放循环、拟态绘制、图标、SQLite 访问、HTTP 下载全部自己实现。
- JSON 解析用 Android 平台自带的 `org.json`，不额外引入解析库。
- `sqlite-jdbc` 与 `org.json:json` 只用于 JVM 单元测试，不会进入 APK：
  前者执行真实迁移 SQL，后者提供 `org.json` 的真实实现（Android 的 `android.jar` 里是抛异常的桩）。

---

## 3. 目录结构

```
.
├── .github/workflows/
│   ├── ci.yml                 # PR/手动：单测 + Lint（不产出 APK）
│   └── release.yml            # push tag v*：单测 + Lint + 签名构建 + 发布 Release
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
│       │   ├── core/update/           # 版本比较 / Release 解析 / 下载 / 校验 / 安装
│       │   ├── data/                  # SloganSchema / SloganDatabase / DAO / SettingsStore
│       │   ├── domain/model/          # Preset / PlaybackSettings
│       │   ├── playback/              # PlaybackService（前台服务）
│       │   └── ui/                    # 主题、拟态组件、四个页面、ViewModel
│       ├── main/res/                  # 图标、通知图标、主题、备份规则、FileProvider 路径
│       └── test/                      # 70 个 JVM 单测 + 真实 WAV / GitHub Release 夹具
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

# 单元测试（70 个）
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
2. 提交 PR 到 `main`，`ci.yml` 跑单测与 Lint（不产出 APK）。
3. Review 合并后，在 `main` 上打标签并推送：
   ```bash
   git tag -a v1.2.3 -m "Slogan 1.2.3"
   git push origin v1.2.3
   ```
4. `release.yml` 触发（**这是唯一会构建 APK 的地方**）：
   - 校验标签格式（`v1.2.3` 或 `v1.2.3-rc1`）
   - 单测 + Lint（警告即错误）
   - `versionName = 标签去掉 v`，`versionCode = github.run_number`
   - 解码 keystore → 签名构建 → `apksigner verify` → 校验 mapping
   - 生成 `Slogan-<version>.apk`、`.sha256`、`mapping-<version>.txt.gz`
   - `gh release create` 自动发布 Release

### 触发策略：构建只认 tag

| 事件 | 是否触发 | 跑什么 |
| --- | --- | --- |
| 推送 `main` | **否** | 什么都不跑 |
| Pull Request → `main` | 是 | 单测 + Lint |
| 手动 `workflow_dispatch` | 是 | 单测 + Lint |
| 推送 `v*` 标签 | 是 | 单测 + Lint + 签名构建 + 发布 Release |

推送 `main` 不触发任何工作流，所以合并代码后不会自动产出 APK——**要出包必须打 tag**。
相应地，资源合并、R8 混淆这类只在打包阶段暴露的问题不会在 PR 阶段被拦截，
发版前建议本地先跑一次 `./gradlew assembleRelease`。

### 为什么 mapping 要压缩后再发布

R8 的 `mapping.txt` 是纯文本，每一行都带完整类名与方法签名。本项目 release 产出的
`mapping.txt` 约 24 MB、19.9 万行——相对于 1.1 MB 的 APK 显得很夸张，但这是 R8
重命名了全部依赖（Compose、Kotlin、AndroidX）后的正常体量，不是配置写错了。

文本重复度极高，`gzip -9` 后约 1.7 MB（约为原始体积的 7%）。所以 `release.yml`
在上传前统一压成 `mapping-<version>.txt.gz`，并用 `gzip -t` 校验压缩包完整性。

解压还原崩溃栈：

```bash
gunzip -c mapping-1.0.0.txt.gz > mapping.txt
# 再把 mapping.txt 交给 Google Play / Firebase / retrace 还原堆栈
```

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

### 8.2 单元测试（70 个，全部通过）

| 测试类 | 数量 | 覆盖内容 |
| --- | --- | --- |
| `WavCodecTest` | 9 | 真实 ffmpeg 文件、LIST 块、奇数块填充、WAVE_FORMAT_EXTENSIBLE、8 bit/float 拒绝、data 长度非法、编解码往返 |
| `LoudnessProcessorTest` | 6 | 归一化目标、软限幅不削顶、首尾淡入淡出、静音、立体声布局、参数校验 |
| `PlaybackTimelineTest` | 5 | 总时长、最后一轮无间隔、单次播放、无限循环、零间隔 |
| `SynthesisKeyTest` | 6 | 键稳定性、文本/音色/语速/音调/引擎变化必须换键 |
| `DatabaseMigrationTest` | 7 | v1 数据保留、迁移后 schema 与全新安装一致、默认值、非破坏性、拒绝降级、缺迁移报错 |
| `VersionNameTest` | 10 | `v` 前缀、预发布标识、构建元数据忽略、非法输入、逐段比较、semver 预发布优先级 |
| `GitHubReleaseParserTest` | 10 | 真实 Release 响应夹具、资产选择、digest 解析、缺附件/缺 APK/非法 JSON/坏版本号报错 |
| `ApkVerifierTest` | 5 | 真实文件 SHA-256 与系统 `shasum` 一致、空文件标准值、校验和大小写无关 |
| `UpdateStateTest` | 3 | 下载进度比例、总字节未知、超范围钳制 |
| `TextScriptTest` | 9 | 中文判定：纯中文、中英混排、纯英文/数字、纯标点、扩展 A 区、中文语言标签变体 |

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
| 真机运行（基础播放） | **已由用户实机验证**：中文音色可正常循环播报。同时暴露并修复了两个问题——英文音色朗读中文导致闪退、切换音色听感不变（见「音色与语言匹配」） |
| 真机运行（更新链路） | **未验证**。检查/下载/校验/安装只做了单测与二进制验证，尚未在真机走完整流程 |
| 前台服务保活 / 锁屏循环 | **未验证**。取决于厂商后台策略，需要在真机上长时间息屏观察 |
| CI 环境变量签名通道 | 已由两次真实发版验证（v1.0.0 / v1.1.0），`apksigner verify` 通过 |
| 不同 TTS 引擎的 WAV 输出 | 仅支持 16 bit 线性 PCM WAV；若某引擎输出 float/8 bit/非 WAV，会抛出明确错误而不是静默播放失败 |
| 实际扩音响度 | 取决于设备扬声器功率、系统音效与媒体音量，代码只保证不削顶、平均响度尽量高 |
| 音频输出路由 | 走系统媒体通道；连接耳机/蓝牙时会从对应设备播放，不会强制切回扬声器（强制切换需要 `MODE_IN_COMMUNICATION`，会显著改变音质且难以在无设备环境下验证） |
| 应用内更新端到端 | **未执行**。检查/下载/校验/安装全链路只做了单元测试与二进制验证（见 8.6），没有真机跑过实际安装。首次在真机验证时重点看：下载进度是否平滑、`FileProvider` 授权是否被系统接受、未知来源授权跳转是否落到本应用 |
| GitHub API 限流 | 未认证请求每小时 60 次。个人使用足够；若频繁点击「检查更新」会收到 403，界面提示「访问过于频繁，请稍后再试」 |

### 8.6 更新链路的验证边界

| 环节 | 验证方式 | 结论 |
| --- | --- | --- |
| 版本比较 | 10 个单测覆盖 `v` 前缀、预发布、逐段比较 | 通过 |
| Release 解析 | 用真实 v1.0.0 发布的结构化夹具（资产名/字节数/digest 均与线上一致） | 通过 |
| SHA-256 计算 | 对真实 WAV 文件计算，与系统 `shasum -a 256` 逐位比对 | 通过 |
| 进度计算 | 单测覆盖比例、总字节未知、越界钳制 | 通过 |
| HTTP 抓取 | **未在本机执行**——GitHub API 对本机出口 IP 限流，无法取到 200 响应 | 未验证 |
| 签名比对 / 安装跳转 | 依赖 `PackageManager` 与系统安装器，JVM 单测无法覆盖 | 未验证 |

`UpdateClient`、`ApkDownloader`、`ApkInstaller` 的失败路径都收敛为可读中文提示，
网络异常不会让界面停在「下载中」不动。

---

## 9. 权限与支持范围

| 权限 | 用途 |
| --- | --- |
| `FOREGROUND_SERVICE` | 后台循环播放 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ 声明前台服务类型 |
| `POST_NOTIFICATIONS` | Android 13+ 展示常驻播放通知（拒绝仍可播放，但通知栏不可见） |
| `WAKE_LOCK` | 播放期间防止息屏休眠中断循环 |
| `INTERNET` | 仅在用户点击「检查更新」时访问 GitHub API 与下载地址 |
| `REQUEST_INSTALL_PACKAGES` | 下载完成后拉起系统安装器覆盖安装 |

网络权限只服务于应用内更新：应用不做任何统计上报，也不会在后台自动联网。
语音合成由系统 TTS 引擎负责，不经过本应用的网络请求。

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
| 应用内更新 | `core/update/`（检查 / 下载 / 校验 / 安装）、`ui/screens/UpdateCard.kt` |
| 签名双通道 | `app/build.gradle.kts`、`.github/workflows/release.yml` |
| 零警告 | `app/build.gradle.kts`、`app/lint.xml` |

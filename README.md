# Apertus

轻量级跨平台音乐播放器骨架。漂亮、现代、代码精简、方便后续接入自有 API。

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Material 3 | CMP 内置 |
| Ktor Client | 3.6.0 |
| kotlinx.serialization | 1.11.0 |
| Coil | 3.6.3 |
| Gadulka | 1.14.0 |

## 支持平台

- Android
- iOS
- Windows / macOS / Linux（Desktop JVM）

> 第一版不包含 Web。

## 项目结构

```
composeApp/src/commonMain/kotlin/com/apertus/music/
├── App.kt                  # 应用入口：组装依赖、导航、响应式布局
├── model/
│   └── Track.kt            # 唯一数据模型
├── data/
│   ├── MusicApi.kt         # Ktor API 薄层（含 Base URL 配置）
│   ├── MusicRepository.kt  # 给上层提供歌曲数据
│   └── FakeMusicRepository.kt  # 测试数据（4 首示例歌曲）
├── player/
│   ├── PlayerController.kt # 播放器最小抽象接口
│   ├── GadulkaPlayerController.kt  # Gadulka 适配层
│   ├── SeekGate.kt         # 跳转后的位置闸门（防进度条回弹）
│   └── TimeFormat.kt       # m:ss / h:mm:ss / -m:ss 格式化（可单测）
├── state/
│   ├── AppState.kt         # 导航 + 歌曲列表状态
│   └── PlayerStore.kt      # 当前歌曲、播放状态、控制
├── ui/
│   ├── HomeScreen.kt       # 歌曲列表
│   ├── PlayerScreen.kt     # 全屏播放器
│   └── SettingsScreen.kt   # 设置（API URL / 版本 / 关于）
├── components/
│   ├── TrackItem.kt        # 列表项
│   ├── MiniPlayer.kt       # 底部迷你播放器
│   ├── EqualizerBars.kt    # 跳动音柱（缓冲指示 + 正在播放标记）
│   └── Artwork.kt          # Coil 封面统一封装
└── theme/
    ├── AppTheme.kt         # Apertus 配色 / 圆角 / 字阶
    └── ApertusIcons.kt     # 自绘 24dp 图标集

composeApp/src/commonTest/kotlin/com/apertus/music/
├── player/SeekGateTest.kt        # 位置闸门
├── player/TimeFormatTest.kt      # 时间格式
├── player/PerformanceTest.kt     # 热路径性能护栏
└── state/PlayerStoreTest.kt      # 含「跳转立刻可见」的回归测试
```

## 架构

```
UI → State/Store → Repository → API (Ktor)
              ↓
         PlayerStore → PlayerController → Gadulka → 平台原生播放器
```

- UI 只和 `AppState` / `PlayerStore` 交互
- 更换播放库只需修改 `GadulkaPlayerController`
- 更换 API 只需修改 `MusicApi` / `MusicRepository`

## 界面与动效

配色、圆角、字阶都写在 `theme/AppTheme.kt`，是一套手写的 Apertus 品牌色，
而不是 Android 动态取色 —— 这个 App 需要自己的辨识度。

- **配色是量过的。** 浅色 / 深色两套 `ColorScheme` 的每一组前景/背景都跑过
  WCAG 对比度计算，**最低 4.5:1**（正文门槛），实测最紧的一组是 5.88:1。
  改颜色时请一并复核对比度。
- **形状与字阶。** `Shapes` 从 8dp 到 36dp 递增；`Typography` 在 Material 3
  默认字阶上把标题加粗、字距收紧一点。
- **图标是自绘的。** `theme/ApertusIcons.kt` 里是一组 24dp `ImageVector`，
  所以 `material-icons-extended` 已经从依赖里删掉了（它被 Compose 插件钉在
  1.7.3，上游明确不再更新）。要加图标就在这个文件里加一条路径。
- **动效。** 全屏播放器是 `AnimatedVisibility` 从底部滑入的浮层（而不是替换
  整个屏幕），所以返回时曲库不用重建、封面不用重下；曲库与设置之间用
  `Crossfade`；正在播放的那一行会轻微放大并显示跳动的音柱；迷你播放器有
  一条随进度平滑推进的细线。

## 如何运行

### Android（推荐：交给 GitHub Actions）

本项目不在本地编译 Android 包。每次 push 到 `main` 都会自动触发
[`.github/workflows/android.yml`](.github/workflows/android.yml)：

1. **Build debug APK** — `./gradlew :composeApp:assembleDebug`，产物上传为
   artifact `apertus-debug-apk`。
2. **Run unit tests** — `./gradlew :composeApp:desktopTest`，跑 `commonTest` 里的
   跳转逻辑 / 时间格式 / 性能护栏。跑在 JVM 上，不需要模拟器。
3. **Verify the built APK** — APK 完整性闸门，见下面的「APK 完整性校验」。
4. **Launch smoke test + performance gate (emulator)** — 把刚构建出来的 APK 装进
   Android 模拟器，通过 LAUNCHER intent 启动（也就是用户点图标的那条路径），
   检查 `logcat` 与 Android crash buffer（出现 `FATAL EXCEPTION` 直接失败），
   通过后再跑性能闸门，见下面的「性能闸门」。

上面每一步都是**必须过**的：前一步失败，后面的步骤不会被执行，job 直接红。

下载 APK：

```bash
gh run list --repo Maicy0609/Apertus-with-md3
gh run download <run-id> --name apertus-debug-apk
```

或者直接在网页上打开 Actions → 某次运行 → Artifacts。

也可以手动触发：Actions → Android CI → Run workflow（`workflow_dispatch`）。

> 只改文档（`**.md` / `docs/**` / `LICENSE` / `.gitignore`）的提交**不会**触发
> CI —— workflow 里配了 `paths-ignore`，这类 push 连一次运行都不会创建。
> 既改文档又改代码的提交照常触发。

### Desktop

```bash
./gradlew :composeApp:run
```

### iOS

仓库目前**还没有** `iosApp/` Xcode 工程。`commonMain` 与 `iosMain` 已经就绪
（`MainViewController.kt` 是留给 Swift 侧的入口），需要自行新建 Xcode 工程
并把 Kotlin 框架接进去。

## Android 构建注意事项

> **`android:name` 固定写全限定名。**
>
> `AndroidManifest.xml` 里的相对名（`.MainActivity`）是按 AGP 的 `namespace`
> 解析的，**不是**按 Kotlin 源码的包名解析的。两者不一致时（历史上
> `namespace` 是 `app.melody`，Kotlin 包却是 `app`），`.MainActivity` 会让系统去找
> `app.melody.MainActivity` 这个并不存在的类，启动瞬间抛
> `ClassNotFoundException`，表现就是「点开图标立刻退出」。
> 现在 `namespace` / `applicationId` / Kotlin 包都是 `com.apertus.music`，
> 相对名也能工作，但这里仍然固定写全限定名，免得以后两者再分叉。
>
> CI 的 **Verify the built APK** 步骤会拿清单里点名的每一个组件类去 DEX 里核对，
> 发现悬空引用直接让流水线失败 —— 这条静态闸门就是这类事故的回归测试。

改包名时请同时确认：

- `composeApp/build.gradle.kts` 的 `namespace` / `applicationId`
- Kotlin 源码目录 `composeApp/src/*/kotlin/com/apertus/music/` 与各文件的 `package`
- `AndroidManifest.xml` 中 activity 的全限定名
- `.github/scripts/smoke-test.sh` 顶部的 `PACKAGE` / `EXPECTED_ACTIVITY`
- `.github/scripts/verify-apk.sh` 顶部默认的 `PACKAGE` / `EXPECTED_ACTIVITY`
  （`tools/apkcheck.py` 不用改，类名是现场从清单和 DEX 里读的）

> **`compileSdk` 还需要 `compileSdkMinor`。**
>
> API 37 是第一个带次版本号的 Android SDK，Google 只发布了
> `platforms;android-37.0` / `37.1` / `37.2`，**没有** `platforms;android-37`。
> 所以 `composeApp/build.gradle.kts` 里除了 `compileSdk = 37` 还必须写
> `compileSdkMinor = 0`，否则 AGP 会去找并不存在的 `android-37`，
> 报 `Failed to find target with hash string 'android-37'`。

## APK 完整性校验

`build` job 打完包后会跑 [`.github/scripts/verify-apk.sh`](.github/scripts/verify-apk.sh)，
它和 [`tools/apkcheck.py`](tools/apkcheck.py) 一起覆盖下面这些项目。
所有原始输出都会写进 `verify/`，并随 `build-reports` artifact 一起上传，便于事后核对。

| 检查 | 工具 | 拦住什么 |
|---|---|---|
| ZIP 容器 / 条目 CRC | `unzip -tqq` + `tools/apkcheck.py` | 坏块、条目截断、CRC 错误 |
| 每个 `classes*.dex` 的魔数与头部自洽 | `tools/apkcheck.py`（纯 Python，自己解 dex，不依赖外部工具） | 不是合法 dex、`file_size` / `header_size` / `endian_tag` 不自洽 |
| `resources.arsc` 存在且未压缩 | `tools/apkcheck.py` | targetSdk ≥ 30 的资源表问题 |
| 4 字节对齐、`.so` 4096 页对齐 | `zipalign -c -v 4` + `tools/apkcheck.py` | 未对齐导致安装失败或 native 库加载失败 |
| 签名有效性 | `apksigner verify --verbose --print-certs` | 签名缺失/损坏，根本装不上 |
| package / launcher / 权限 | `aapt2 dump badging` | 包名、入口 Activity、`INTERNET` 权限被改坏 |
| **清单点名的类是否真的在 DEX 里** | `tools/apkcheck.py` | **「打开就闪退」：悬空的 `android:name`** |
| dex 里确实定义了 launcher 类 | `apkanalyzer dex packages --defined-only` | 与上一条交叉确认 |

最后两条是这次事故的静态回归闸门。清单里写了一个不存在的类名时，
编译、打包、签名**全都会成功**，只有把 APK 装到设备上、点开的那一刻才会抛
`ClassNotFoundException`。所以「APK 合法」不能只看能不能构建出来，
必须显式验证 **清单里点名的类 == DEX 里真实存在的类**。

`tools/apkcheck.py` 也可以单独用：

```bash
python3 tools/apkcheck.py app-debug.apk --manifest AndroidManifest.xml \
  --expect-package com.apertus.music \
  --expect-activity com.apertus.music.MainActivity
```


## 性能闸门

性能是一个指标，所以它进了 CI，而且**不过就红**。

两层：

| 层 | 跑在哪 | 脚本 | 挡住什么 |
|---|---|---|---|
| 热路径护栏 | JVM（`desktopTest`） | `composeApp/src/commonTest/.../PerformanceTest.kt` | 有人把跳转路径写成线性扫描、每次轮询都分配一堆对象之类的无界开销 |
| 真机指标 | Android 模拟器 | [`.github/scripts/perf-test.sh`](.github/scripts/perf-test.sh) | 冷启动 / 热启动 / 内存 / ANR 回归 |

`perf-test.sh` 的预算（脚本顶部可调）：

| 指标 | 预算 | 说明 |
|---|---|---|
| 冷启动 `TotalTime` | ≤ 9000 ms | `am start -W`，force-stop 之后冷起 |
| 热启动 `TotalTime` | ≤ 3000 ms | 已经在后台，按 HOME 再切回来 |
| 总 PSS | ≤ 614400 kB（600 MB） | `dumpsys meminfo` |
| 进程存活 | 必须 | 顶部 Activity 还是我们、logcat 里没有 `ANR in` / `FATAL EXCEPTION` |

细节：

- 每项取 **3 次里的最小值**，避免被模拟器抖动误伤。
- 脚本开头会把 `window/transition/animator_duration_scale` **重新设回 1.0**。
  smoke 任务用 `disable-animations: true` 跑是刻意的（要确定性），但性能闸门
  不能这么干 —— 否则量的是「动画全关」这个用户根本见不到的快版本。
- 量之前会先 `adb logcat -b all -c` 清空日志缓冲，崩溃 / ANR 判定只看本次运行，
  不会把上一步冒烟留下的日志算到自己头上；`ANR in <包名>` 按包名匹配（这条由
  ActivityManager 自己写，pid 不是 App 的），`FATAL EXCEPTION` 则从 **按 pid 过滤**
  的 dump 里读，避免被别的进程的崩溃误伤。
- `gfxinfo` 只打印、不判定：模拟器是 swiftshader 软件渲染，帧率数字反映的是
  模拟器速度而不是 App 开销。
- 输出写在 `perf/`（`summary.txt` / `meminfo.txt` / `logcat.txt` / `logcat-full.txt` /
  `gfxinfo.txt`），随 artifact `emulator-performance` 上传，不管成功失败都传。
- 模拟器任务里 smoke 和 perf 是同一个 `script` 块，前面有 `set -e`：这个 `set -e`
  是必须的，否则 smoke 失败了 perf 还会接着跑，job 反而变绿。

## 如何修改 API Base URL

编辑 `composeApp/src/commonMain/kotlin/com/apertus/music/data/MusicApi.kt`：

```kotlin
const val API_BASE_URL = "https://your-api.com/api"
```

然后将 `App.kt` 中的 `FakeMusicRepository()` 替换为：

```kotlin
val client = HttpClient {
    install(ContentNegotiation) { json() }
}
MusicRepository(KtorMusicApi(client, API_BASE_URL))
```

## 播放器

基于 [Gadulka](https://github.com/kkostov/gadulka) 1.14.0，封装在 `GadulkaPlayerController` 中。
UI 不直接调用 Gadulka，通过 `PlayerController` 接口解耦。

第一版支持：play / pause / resume / stop / seek / 进度 / 时长 / 播放状态。

## 版本

0.1.0（骨架版）

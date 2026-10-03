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
│   └── GadulkaPlayerController.kt  # Gadulka 适配层
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
│   └── Artwork.kt          # Coil 封面统一封装
└── theme/
    └── AppTheme.kt         # Material 3 深/浅色主题
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

## 如何运行

### Android（推荐：交给 GitHub Actions）

本项目不在本地编译 Android 包。每次 push 到 `main` 都会自动触发
[`.github/workflows/android.yml`](.github/workflows/android.yml)：

1. **Build debug APK** — `./gradlew :composeApp:assembleDebug`，产物上传为
   artifact `apertus-debug-apk`。
2. **Verify the built APK** — APK 完整性闸门，见下面的「APK 完整性校验」。
3. **Launch smoke test (emulator)** — 把刚构建出来的 APK 装进 Android 模拟器，
   通过 LAUNCHER intent 启动（也就是用户点图标的那条路径），然后检查
   `logcat` 与 Android crash buffer，出现 `FATAL EXCEPTION` 就直接让流水线失败。

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

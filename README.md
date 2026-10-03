# Melody (Apertus-with-md3)

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
composeApp/src/commonMain/kotlin/app/
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
   artifact `melody-debug-apk`。
2. **Launch smoke test (emulator)** — 把刚构建出来的 APK 装进 Android 模拟器，
   通过 LAUNCHER intent 启动（也就是用户点图标的那条路径），然后检查
   `logcat` 与 Android crash buffer，出现 `FATAL EXCEPTION` 就直接让流水线失败。

下载 APK：

```bash
gh run list --repo Maicy0609/Apertus-with-md3
gh run download <run-id> --name melody-debug-apk
```

或者直接在网页上打开 Actions → 某次运行 → Artifacts。

也可以手动触发：Actions → Android CI → Run workflow（`workflow_dispatch`）。

### Desktop

```bash
./gradlew :composeApp:run
```

### iOS

仓库目前**还没有** `iosApp/` Xcode 工程。`commonMain` 与 `iosMain` 已经就绪
（`MainViewController.kt` 是留给 Swift 侧的入口），需要自行新建 Xcode 工程
并把 Kotlin 框架接进去。

## Android 构建注意事项

> **`android:name` 必须写全限定名。**
>
> `AndroidManifest.xml` 里的相对名（`.MainActivity`）是按 AGP 的
> `namespace`（这里是 `app.melody`）解析的，**不是**按 Kotlin 源码的包名
> （这里是 `app`）解析的。写成 `.MainActivity` 会让系统去找
> `app.melody.MainActivity` 这个并不存在的类，启动瞬间抛
> `ClassNotFoundException`，表现就是「点开图标立刻退出」。
> 所以这里固定写成 `android:name="app.MainActivity"`。

同理，改包名时请同时确认：

- `composeApp/build.gradle.kts` 的 `namespace` / `applicationId`
- `AndroidManifest.xml` 中 activity 的全限定名
- 冒烟测试脚本 `.github/scripts/smoke-test.sh` 顶部的 `EXPECTED_ACTIVITY`

> **`compileSdk` 还需要 `compileSdkMinor`。**
>
> API 37 是第一个带次版本号的 Android SDK，Google 只发布了
> `platforms;android-37.0` / `37.1` / `37.2`，**没有** `platforms;android-37`。
> 所以 `composeApp/build.gradle.kts` 里除了 `compileSdk = 37` 还必须写
> `compileSdkMinor = 0`，否则 AGP 会去找并不存在的 `android-37`，
> 报 `Failed to find target with hash string 'android-37'`。


## 如何修改 API Base URL

编辑 `composeApp/src/commonMain/kotlin/app/data/MusicApi.kt`：

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

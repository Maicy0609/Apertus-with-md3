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

### Desktop

```bash
./gradlew :composeApp:run
```

### Android

```bash
./gradlew :composeApp:installDebug
```

### iOS

在 Xcode 中打开 `iosApp/iosApp.xcodeproj`，选择模拟器或设备运行。

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

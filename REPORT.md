# Apertus 修复报告

> 目标：**编译出来的 APK 点开图标就直接退出**。顺便把全部编译/校验工作搬到
> GitHub Actions，并把项目/包名理清。
>
> 仓库：<https://github.com/Maicy0609/Apertus-with-md3>（根模块 `Apertus`，应用包名
> `com.apertus.music`）

---

## 0. 结论

| | |
|---|---|
| **病根** | `AndroidManifest.xml` 里 launcher activity 写成了相对名 `.MainActivity`，AGP 按 **`namespace`**（当时是 `app.melody`）而不是按 Kotlin 包名（当时是 `app`）去解析，于是系统去找一个**根本不存在的类** `app.melody.MainActivity` |
| **症状** | `ActivityThread` 实例化 launcher activity 时抛 `ClassNotFoundException`，进程在第一帧之前就死掉 —— 就是"点开立刻退出" |
| **修法** | `android:name` 一律写全限定名；同时把包名统一成 `com.apertus.music`，让 `namespace` / `applicationId` / Kotlin 包三者一致 |
| **加固** | 启动路径不再依赖 Gadulka 的构造（改懒加载 + `release()`）；补上 `kotlinx-coroutines-android`（否则 `Dispatchers.Main` 会抛 "Module with the Main dispatcher had failed to initialize"）；错误状态会显示在播放页 |
| **构建** | 全部在 GitHub Actions 完成，本地不编译。`.github/workflows/android.yml` 两个 job：`Build debug APK` + `Launch smoke test + performance gate (emulator)` |
| **新增闸门** | 一共四道：源码编码体检（`check-sources.sh`）、单元测试（`desktopTest`）、APK 完整性校验（`tools/apkcheck.py` + `.github/scripts/verify-apk.sh`）、模拟器启动冒烟 + 性能预算（`smoke-test.sh` / `perf-test.sh`）。APK 校验里最关键的一条是**清单里点名的每个组件类是否真的存在于 DEX 里** —— 这正是本次事故的静态回归测试 |
| **界面与性能** | 跳转延迟从「要等 2 s」改成即时（§8）；MD3 Expressive 导航组件 + 自绘图标 + 动效（§9）；冷启动 / 热启动 / 内存 / ANR 四条真机预算进 CI（§10） |

### 流水线历史（含失败）

| Run | Commit | 结果 | 说明 |
|---|---|---|---|
| 37118422907 | `0311b8e` | ✗ | `android-actions/setup-android@v3` 要装已被删除的 `tools` 包 |
| 37118960187 | `6d20b9c` | ✓✓ | 首个全绿：构建 + 模拟器启动冒烟通过 |
| 37119520434 | `106940f` | ✓✓ | `sdkmanager` 改为绝对路径定位 |
| 37119751636 | `d3693e4` | 部分 | build ✓；smoke 被后一次 push 的 `concurrency: cancel-in-progress` 取消 |
| 37119927925 | `54fa0f7` | 取消 | 被 @Maicy0609 手动取消 |
| 37120644505 | `6eac01f` | ✗ | **源码被写坏**：19 个 `.kt` 全被加 BOM，5 个文件非 ASCII 文本成了 GBK 乱码 |
| 37120774195 | `c066935` | ✗ | 编码修好了、编译通过；新加的 APK 闸门里有一条 `grep` 写错，误判 |
| 37120911164 | `f12b40a` | 部分 | build ✓（含 verify ✓）；smoke ✗ —— ComponentName 短格式导致字符串比较失败 |
| 37121121961 | `aa0a799` | ✓✓ | 编码体检 + 构建 + APK 校验 + 模拟器冒烟全部通过 |
| 37122352535 | `39fdc3e` | 部分 | build ✓（修掉 `PathFillType` / `animateFloat` 后编译通过）；`Run unit tests` ✗ —— `UncompletedCoroutinesError` |
| 37122983341 | `64d07fe` | 部分 | build ✓；`Run unit tests` ✗ —— 3 个用例断言失败：`backgroundScope` 里的协程不会被 `advanceUntilIdle()` 驱动 |
| 37123239623 | `7832ae5` | **✓✓** | **最终全绿**：26 个单测全过 + 8 项 APK 校验 + 模拟器冒烟 + 性能闸门（冷启动 1584 ms / 热启动 163 ms / PSS 103485 kB） |
| — | `4114968`、`e645387`、`044b324` | 未触发 | 纯 `.md` / `.gitignore` 提交，按 `paths-ignore` 规则不产生 run（已确认生效） |

失败记录保留在这里是有意的：**这次事故的教训就是"编译通过"证明不了任何事**。

---

## 1. 根因分析

### 1.1 相对类名是按 `namespace` 解析的，不是按 Kotlin 包名

出事时的配置：

```kotlin
// composeApp/build.gradle.kts
namespace = "app.melody"          // AGP 的命名空间
applicationId = "app.melody"
```

```xml
<!-- composeApp/src/androidMain/AndroidManifest.xml -->
<activity android:name=".MainActivity" android:exported="true"> ... </activity>
```

而 Kotlin 源码的包是 `app`（`composeApp/src/androidMain/kotlin/app/MainActivity.kt`）。

Android 的规则是：**相对名（以 `.` 开头）拼接到 manifest 的 `package` 上**。AGP 在合并
manifest 时，`package` 取自 `namespace`，所以 `.MainActivity` 展开成了
`app.melody.MainActivity` —— 这个类不存在。真正的类叫 `app.MainActivity`。

于是启动时：

1. 用户点图标 → 系统按 `LAUNCHER` intent 找到 `app.melody/.MainActivity`；
2. `ActivityThread` 反射加载该类 → `ClassNotFoundException`；
3. 进程在任何 **应用代码执行之前**就崩了，所以看不到任何界面、也没有自绘的错误页。

这解释了"立刻退出"这个现象：不是播放器崩、不是网络崩，而是**入口本身不存在**。

> 这类 bug 的恶心之处：Gradle 编译、D8 打包、`apksigner` 签名、`zipalign` **全部成功**。
> 只有把 APK 装到设备上点开的那一刻才炸。所以"能构建出来"完全不能作为验收标准。

### 1.2 顺带排查出的两个隐患

**(a) 启动即构造平台播放器。**
`App.kt` 原来在首次组合时就 `remember { GadulkaPlayer() }`。而 Gadulka 的 Android
实现是无参构造里**立刻** `ExoPlayer.Builder(ContextProvider.getContext()).build()`
（`androidcontextprovider` 的 `ContentProvider` 会提前初始化 context，所以通常没事，
但一旦 media3 没进 classpath，抛的是 `NoClassDefFoundError`，依然是启动即死）。

改法：`GadulkaPlayerController` 接受一个 `playerFactory`，**第一次真正要播的时候**才建后
端；构造失败会被 catch 成 `PlayerStatus.Error`，在播放页显示出来，而不是静默闪退。
新增 `PlayerController.release()`，`App.kt` 里用 `DisposableEffect { onDispose { ... } }`
释放。实测 Gadulka 构造反编译确认：字段 `mediaPlayer` 由 `init { setup() }` 触发，
`ContextProvider.getContext()` 早于 `ExoPlayer.Builder`。

**(b) `Dispatchers.Main` 没有实现。**
`AppState` / `PlayerStore` / `GadulkaPlayerController` 都用 `Dispatchers.Main`，但
`commonMain` 只声明了 `kotlinx-coroutines-core` —— 那是个没有 Main 实现的纯多平台库。
在 Android 上这会抛
`Module with the Main dispatcher had failed to initialize. For tests Dispatchers.setMain from kotlinx-coroutines-test module can be used`。
改法：`androidMain` 补上 `kotlinx-coroutines-android`（并加进版本目录）。

---

## 2. 改动清单

### `0311b8e` — 修根因 + 建 CI（15 files, +408/−36）

| 文件 | 改动 |
|---|---|
| `composeApp/src/androidMain/AndroidManifest.xml` | `android:name` 改为全限定名；加 `supportsRtl`、`WAKE_LOCK`、`configChanges`、`windowSoftInputMode`；主题指向 `@style/Theme.Apertus` |
| `.../androidMain/res/values/themes.xml`、`values-night/themes.xml` | 新增。基于 framework `@android:style/Theme.Material.*.NoActionBar`（`ComponentActivity` 不需要 AppCompat/MaterialComponents），窗口背景与 Compose 主题一致，避免冷启动白闪 |
| `.../androidMain/res/values/colors.xml`、`values-night/colors.xml` | 新增。窗口背景色 `#FFFBFE` / `#1C1B1F` |
| `commonMain/player/PlayerController.kt` | 接口新增 `fun release()` |
| `commonMain/player/GadulkaPlayerController.kt` | 重写：懒建后端、`setOnErrorListener`、300ms 轮询、错误不覆盖、构造失败降级为 Error 状态 |
| `commonMain/App.kt` | 去掉 eager 构造，改 `remember { GadulkaPlayerController() }` + `DisposableEffect { release() }` |
| `commonMain/ui/PlayerScreen.kt` | 显示 `PlayerStore.error` |
| `composeApp/build.gradle.kts` | `androidMain` 加 `kotlinx-coroutines-android` |
| `gradle/libs.versions.toml` | 新增 `kotlinx-coroutines-android` |
| `gradle.properties` | `-Xmx2048m` → `-Xmx4096m` |
| `.gitattributes` | 新增。`*.sh`/`*.yml`/`gradlew` 强制 LF（宿主 `core.autocrlf=true` 否则会把 CRLF 带进 CI 脚本） |
| `.github/workflows/android.yml` | 新增 |
| `.github/scripts/smoke-test.sh` | 新增 |
| `README.md` | 补充说明 |

### `6d20b9c` — CI 不能用 setup-android（3 files, +112/−28）

删掉 `android-actions/setup-android@v3`（它会执行 `sdkmanager tools`，而 `tools` 包已经
从 SDK 仓库里消失了），改成自己定位 runner 预装的 SDK。
同时 `composeApp/build.gradle.kts` 加 `compileSdkMinor = 0`（见 §5.2）。

### `106940f` — `sdkmanager` 不在 PATH 上（1 file, +11/−1）

改为按绝对路径依次探测：`$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager` →
`command -v sdkmanager` → `$SDK_ROOT/cmdline-tools/16.0/bin/sdkmanager` → `find`。

### `d3693e4` — 文档（1 file, +11/−1）

记录 `compileSdkMinor`；删掉 README 里那个并不存在的 `iosApp/iosApp.xcodeproj` 指引。

### `54fa0f7` — Melody → Apertus（13 files, +51/−32）

见 §3。

### `6eac01f` — 包名统一 + APK 闸门（28 files, +647/−101）

见 §3.2 与 §6。

### `c066935` / `f12b40a` / `aa0a799` — 修自己引入的问题

见 §5.6 ~ §5.8。

### `e0654cd` / `39fdc3e` / `c0734cb` / `64d07fe` / `4a12fcf` / `7832ae5` — 跳转延迟、Expressive 界面与性能闸门

见 §8 ~ §10。这几笔把三件事一起做完：seek 从「等 2 s」改成即时、MD3 Expressive
的导航组件与自绘图标、以及把冷启动 / 热启动 / 内存 / ANR 四条预算搬进 CI。

其中后两笔是修 CI 自己暴露出来的问题：`4a12fcf` 把单测里的 `backgroundScope`
换成 `CoroutineScope(UnconfinedTestDispatcher(testScheduler))`（前者的协程不会被
`advanceUntilIdle()` 驱动，3 个用例因此断言到陈旧状态）；`7832ae5` 把性能闸门的
崩溃 / ANR 判定按包名和 pid 收窄，避免被别的进程的日志误伤。

---

## 3. 改名与包名

### 3.1 Melody → Apertus（`54fa0f7`）

| 位置 | 改动 |
|---|---|
| `settings.gradle.kts` | `rootProject.name = "melody"` → `"Apertus"` |
| `composeApp/build.gradle.kts` | `namespace` / `applicationId` → `app.apertus` |
| `AndroidManifest.xml` | `android:label` → `Apertus`；`android:theme` → `@style/Theme.Apertus` |
| `res/values{,-night}/themes.xml` | `Theme.Melody` → `Theme.Apertus`；`@color/melody_window_background` → `@color/apertus_window_background` |
| `res/values{,-night}/colors.xml` | 颜色名同步 |
| `jvmMain/Main.kt` | 窗口标题 → `Apertus` |
| `ui/HomeScreen.kt` | 顶栏文案 → `Apertus` |
| `ui/SettingsScreen.kt` | 关于页文案 |
| `android.yml` | artifact `melody-debug-apk` → `apertus-debug-apk`（上传+下载两处） |
| `.github/scripts/smoke-test.sh` | `PACKAGE` / `EXPECTED_ACTIVITY` / log tag / grep 模式 |

改完 `git grep -i melody` 对全部纳管文件零命中。

### 3.2 包名 → `com.apertus.music`（`6eac01f`）

原来只有 `namespace` / `applicationId` 改了名，Kotlin 包还叫 `app`，两者随时可能再次分叉
—— 而那个分叉正是本 bug 的成因。所以把 **4 个 source set 的 19 个 `.kt` 文件**整体搬到
`com/apertus/music/`，并把 `package` / `import` 全部改写：

- `composeApp/build.gradle.kts`：`namespace = "com.apertus.music"`、
  `applicationId = "com.apertus.music"`、`mainClass = "com.apertus.music.MainKt"`
- `AndroidManifest.xml`：`android:name="com.apertus.music.MainActivity"`
- `.github/scripts/smoke-test.sh`：`PACKAGE="com.apertus.music"`

现在 `namespace` == `applicationId` == Kotlin 包根，三者一致。
（manifest 里仍然写全限定名，不依赖这个巧合。）

**改包名时要同步改的地方**（README 里也写了）：

1. `composeApp/build.gradle.kts` 的 `namespace` / `applicationId`
2. Kotlin 源码目录与各文件 `package`
3. `AndroidManifest.xml` 里 activity 的全限定名
4. `.github/scripts/smoke-test.sh` 顶部 `PACKAGE` / `EXPECTED_ACTIVITY`
5. `.github/scripts/verify-apk.sh` 顶部默认 `PACKAGE` / `EXPECTED_ACTIVITY`
   （`tools/apkcheck.py` 不用改，类名是现场从清单和 DEX 里读的）

---

## 4. CI 流水线

文件：[`.github/workflows/android.yml`](.github/workflows/android.yml)

### 4.1 触发条件

```yaml
on:
  push:
    branches: [main, master]
    paths-ignore: ['**.md', 'docs/**', 'LICENSE', '.gitignore']
  pull_request:
    branches: [main, master]
    paths-ignore: [...]
  workflow_dispatch:
```

- **纯文档提交不会创建任何 run**（连一次运行都没有），既省额度也省等待时间。
- 文档+代码混合的提交照常跑。
- `.gitattributes` **故意没有**放进 `paths-ignore`：它决定 `gradlew` 和 CI 脚本的行尾，
  改它必须重新验证。
- `concurrency: { group: android-ci-${{ github.ref }}, cancel-in-progress: true }`：
  同一分支连续 push 时，旧 run 会被取消（这也解释了 37119751636 的 smoke job 被取消）。

### 4.2 Job `Build debug APK`

| # | 步骤 | 作用 |
|---|---|---|
| 1 | Checkout / Set up JDK 21 | Temurin 21 |
| 2 | Prepare the Android SDK | 定位 runner 预装的 SDK（`/usr/local/lib/android/sdk`），`yes \| sdkmanager --licenses`，确保 `platform-tools` / `platforms;android-37.0` / `build-tools;37.0.0` 就位，打印已装清单 |
| 3 | Set up Gradle | `gradle/actions/setup-gradle@v6`（带缓存） |
| 4 | Check source encoding | `check-sources.sh`：所有纳管文本文件必须是无 BOM 的合法 UTF-8（§5.6 的回归闸门） |
| 5 | Build debug APK | `./gradlew :composeApp:assembleDebug --stacktrace --no-daemon` |
| 6 | **Run unit tests** | `./gradlew :composeApp:desktopTest --stacktrace --no-daemon`，跑 `commonTest` 里的跳转逻辑 / 时间格式 / 性能护栏。跑在 JVM 上，不需要模拟器 |
| 7 | **Verify the built APK** | `verify-apk.sh`，见 §6 |
| 8 | Upload debug APK | artifact `apertus-debug-apk`，`if-no-files-found: error` |
| 9 | Upload build reports | `if: always()`，含 `verify/**`，便于失败后取证 |

每一步都是**必须过**的：前一步失败，后面的步骤不会被执行，job 直接红。

### 4.3 Job `Launch smoke test + performance gate (emulator)`

`needs: build`。下载上一步的 APK，起 API 34 / x86_64 / `google_apis` 的模拟器
（`-gpu swiftshader_indirect -no-window -no-snapshot-save`，先写
`/etc/udev/rules.d/99-kvm4all.rules` 开 KVM），然后跑
[`.github/scripts/smoke-test.sh`](.github/scripts/smoke-test.sh)：

1. `adb install -r -t` 安装；
2. `cmd package resolve-activity` 解析 launcher，**断言它等于期望的组件**；
3. 记录起始时间戳；
4. `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1` 启动
   —— 故意**不用** `-n` 指定组件，走的正是用户点图标那条路径；
5. 等 20 秒，检查 `topResumedActivity`、`pidof`；
6. 按时间片抓 `logcat` + 单独的 crash buffer，出现 `FATAL EXCEPTION`、
   `ClassNotFoundException`、`Unable to instantiate activity`、`had failed to initialize`
   或进程已经消失 → 流水线失败。

Why 走 monkey 而不是显式 `am start -n`：后者绕过 manifest，会把这一类 bug 放过去。

冒烟通过后，同一个 `script` 块里紧接着跑
[`.github/scripts/perf-test.sh`](.github/scripts/perf-test.sh)，见 §9。两块之间的
`set -e` 是**必须**的：`reactivecircus/android-emulator-runner` 执行这个脚本块时并不
自带 `set -e`，没有它的话冒烟失败也会被后面的性能步骤盖过去，job 反而变绿。

- 性能报告上传为 artifact `emulator-performance`（`if: always()`，`path: perf/`）。
- 测试日志上传为 artifact `emulator-logcat`（`path: smoke/`）。

---

## 5. 踩过的坑

### 5.1 `android-actions/setup-android@v3` 已不可用

```
[command]/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager tools
Warning: Failed to find package 'tools'
Error: The process '.../sdkmanager' failed with exit code 1
```

`tools` 这个历史包（旧的 `android` 工具集）已经不在 `repository2-3.xml` 里了，而该 action
硬要装它。上游 issue `android-actions/setup-android#537`、PR #538/#547 都与此相关，但
v3 tag 仍在要。**结论：不要用这个 action**；runner 镜像本身已经预装了
`ANDROID_HOME=/usr/local/lib/android/sdk`，直接用它自己的 SDK 即可。

### 5.2 `platforms;android-37` 不存在

API 37 是第一个带**次版本号**的 Android SDK。Google 发布的是：

```
platforms;android-37.0   (rev 2)
platforms;android-37.1   (rev 1)
platforms;android-37.2   (rev 1)
```

没有 `platforms;android-37`。所以 `compileSdk = 37` 必须配
`compileSdkMinor = 0`，否则 AGP 会报
`Failed to find target with hash string 'android-37'`。

已确认 AGP 9.4.1 的 `com.android.build.api.dsl.CommonExtension` 里有
`getCompileSdkMinor` / `setCompileSdkMinor`（同一接口里 `compileSdkVersion` 标注
"Will be removed in AGP 10.0"）。`targetSdkMinor` / `minSdkMinor` **不存在**。

顺带：**没有 API 37 的 system image**（`sys-img2-3.xml` 最高到 `android-36`），
所以模拟器冒烟只能停在 API 34；这不影响 `compileSdk = 37` 的构建。

### 5.3 runner 上 `sdkmanager` 不在 PATH

镜像把 SDK 解在 `/usr/local/lib/android/sdk`，`cmdline-tools` 在带版本号的子目录里。
所以脚本里不能直接写 `sdkmanager`，得显式探测路径。这也是为什么 §4.2 的第 2 步要自己找。

### 5.4 宿主 `core.autocrlf=true` 会把 CRLF 带进 Linux 脚本

`gradlew` / `*.sh` 一旦以 CRLF 进 runner，bash 会因为 `\r` 报错。加 `.gitattributes`
把 `*.sh` / `*.yml` / `gradlew` 钉成 `eol=lf`，`*.bat` 钉成 `eol=crlf`，
二进制（jar/aar/png/webp/jks/keystore）显式标 `binary`。

### 5.5 版本目录里有一批**用不到的、指向不存在版本**的 Compose 别名

`gradle/libs.versions.toml` 里 `compose-runtime` / `foundation` / `material3` / `ui` /
`material-icons-core` / `material-icons-extended` 全部 `version.ref = "compose"`（1.12.1）。
实际上 `material3:1.12.1`、`material-icons-*:1.12.1` 在 Maven 上 **404**。
它们从来没被用过 —— `composeApp/build.gradle.kts` 用的是 Compose Gradle 插件的访问器
（`compose.material3` → `org.jetbrains.compose.material3:material3:1.9.0`，
`compose.materialIconsExtended` → `material-icons-extended:1.7.3`，
其余 → 1.12.1）。属于历史遗留噪音，不影响构建，但容易误导人。

### 5.6 ⚠️ 用 Windows PowerShell 批量改源码 = 永久损坏

批量改包名时用了 `Get-Content -Raw` + `Set-Content -Encoding UTF8`：

- `Set-Content -Encoding UTF8`（PS 5.1）给**每个**文件加 UTF-8 BOM；
- `Get-Content -Raw` 默认按系统 ANSI 代码页（GBK）解码，把 UTF-8 字节读成了乱码，
  再按 UTF-8 写回 → 真正的损坏。

后果：19 个 `.kt` 全被加 BOM，其中 5 个的非 ASCII 文本被写坏。
`PlayerScreen.kt` 里 `"Buffering…"` 变成 `"Buffering鈥?` —— **后面的引号被吞掉了**，于是：

```
e: PlayerScreen.kt:104:55 Syntax error: Expecting '"'.
e: PlayerScreen.kt:105:21 Unresolved reference 'style' on receiver of type 'String'.
```

**教训**：
1. 批量改多字节文本，用 Python（显式 `encoding="utf-8"`），不要用 PowerShell 的
   cmdlet 默认编码；
2. 改完必须**逐文件 diff 校验**，不能只看"命令跑完了"；
3. 已经加了 `check-sources.sh` 当 CI 回归闸门（BOM / 非 UTF-8 / U+FFFD）。

### 5.7 `apkanalyzer dex packages` 的字段是**制表符**分隔的

输出形如：

```
C d 3	3	221	com.apertus.music.MainActivity
M d 1	1	36	com.apertus.music.MainActivity <init>()
```

类名是**最后一个 tab 字段**。最初的实现写了 `grep -qE "^C d .* $CLASS$"`
（空格锚定 + 行尾），永远匹配不到，于是在一个完全正常的 APK 上报了失败。
改成用 `awk -F'\t' '$1 ~ /^C d / && $NF == c'`，并用真实产物验证：
真类 `exit=0`、假类 `exit=1`。

### 5.8 ComponentName 的"短格式"

`cmd package resolve-activity --brief` 用的是 `flattenToShortString()`：当类名以包名为
前缀时会被缩写。

```
com.apertus.music/com.apertus.music.MainActivity   →   com.apertus.music/.MainActivity
```

以前包名是 `app.melody`、类是 `app.MainActivity`，前缀对不上，所以一直打印全名；包名统一
之后就变成短格式了，冒烟脚本的字符串比较因此误报。修法是两边都规范化（把开头的 `.`
展开成包名）再比较。

---

## 6. APK 完整性校验

`.github/scripts/verify-apk.sh` + [`tools/apkcheck.py`](tools/apkcheck.py)。

`tools/apkcheck.py` 只用标准库（`zipfile` + `struct`），不依赖 aapt2/apkanalyzer，
自己解 ZIP 和 DEX，保证在任何 python3 环境里都能跑。

| # | 检查 | 工具 | 拦住什么 |
|---|---|---|---|
| 1 | ZIP 容器完整性 | `unzip -tqq` | 坏块、条目截断、CRC 错误 |
| 2 | package / launcher / 权限 | `aapt2 dump badging` | 包名、入口 Activity、`INTERNET` 权限被改坏 |
| 3 | manifest 可解析 | `apkanalyzer manifest print` | 二进制 manifest 损坏 |
| 4 | 包名一致性 | `apkanalyzer apk summary` | 与 badging 交叉确认 |
| 5 | 对齐 | `zipalign -c -v 4` | 未对齐导致安装失败 |
| 6 | 签名 | `apksigner verify --verbose --print-certs` | 签名缺失/损坏，装不上 |
| 7 | **DEX 合法性 + 悬空引用 + 对齐/压缩** | `tools/apkcheck.py` | 见下 |
| 8 | launcher 类确实在 DEX 里 | `apkanalyzer dex packages --defined-only` | 与第 7 条交叉确认 |

第 7 条 `apkcheck.py` 细项：

- 每个 `classes*.dex`：魔数 `dex\n`、`header_size`、`endian_tag`、`file_size == 实际长度`；
- **清单里点名的每个组件类（`<activity>` / `<activity-alias>` / `<service>` /
  `<receiver>` / `<provider>`，外加 `<activity-alias>` 的 `targetActivity`）是否真的
  存在于 DEX 的类表里** —— 不存在就报
  `悬空引用: <tag android:name="X"> 在 DEX 里不存在 -> 运行期 ClassNotFoundException`；
- `resources.arsc` 存在且未压缩；
- `lib/**/*.so` 必须 STORED 且 4096 页对齐；`resources.arsc` 4 字节对齐；其他 STORED
  条目 4 字节对齐。

**第 7 条的悬空引用检查就是本次事故的静态回归测试。** 它把"编译全绿但点开就崩"
变成了"构建直接失败"。

### 佐证（run 37120774195 的 `verify/apkcheck.txt`）

```
== composeApp/build/outputs/apk/debug/composeApp-debug.apk  (24440500 bytes)
    条目 136 (STORED 75)  native-lib 4  签名文件 <无 v1 签名文件>
    classes.dex: dex 037  header=0x70  class_defs=17067  string_ids=122401
    classes10.dex: dex 037  header=0x70  class_defs=832  string_ids=1047
    ... (共 13 个 dex)
    manifest 点名的 5 个组件类, DEX 里全部存在    DEX 里共 37077 个类
PASS  composeApp-debug.apk
```

`sha256=bf6c33cb67e7f4d23046a84e3a17e635ea5f5485d25163d13af6102949cacb30`

`zipalign` 对 4 个 native 库全部 `(OK)`：

```
23740416 lib/arm64-v8a/libandroidx.graphics.path.so (OK)
23756800 lib/armeabi-v7a/libandroidx.graphics.path.so (OK)
23773184 lib/x86/libandroidx.graphics.path.so (OK)
23789568 lib/x86_64/libandroidx.graphics.path.so (OK)
```

### 本地怎么用

```bash
python3 tools/apkcheck.py app-debug.apk \
  --manifest AndroidManifest.xml \
  --expect-package com.apertus.music \
  --expect-activity com.apertus.music.MainActivity
```

---

## 7. 验证证据

### 7.1 构建产物（run 37118960187）

```
APK=composeApp/build/outputs/apk/debug/composeApp-debug.apk
24440480 字节
package: name='app.melody' versionCode='1' versionName='0.1.0'
         platformBuildVersionName='17' platformBuildVersionCode='37'
         compileSdkVersion='37' compileSdkVersionCodename='17'
launchable-activity: name='app.MainActivity'
```

（这是改名前的产物，包名是 `app.melody`；`launchable-activity` 已经是全限定名
`app.MainActivity`，正是修复的核心。）

### 7.2 模拟器启动冒烟（run 37118960187）

```
== Installing ... Success
resolved: 'app.melody/app.MainActivity'
expected: 'app.melody/app.MainActivity'
== Launching via LAUNCHER intent (monkey)
Events injected: 1
topResumedActivity=ActivityRecord{e3377d8 u0 app.melody/app.MainActivity t8}
pid: '3253'
== main log: 32510 lines | crash buffer: 0 lines
SMOKE TEST PASSED: app.melody resolved to app.melody/app.MainActivity,
                   launched, and is still running (pid 3253).
```

logcat 关键行：

```
START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]
          flg=0x10200000 cmp=app.melody/app.MainActivity}
Start proc 3253:app.melody/u0a192 for next-top-activity
Displayed app.melody/app.MainActivity for user 0: +9s56ms
```

`Displayed ... +9s56ms` 说明第一帧真的画出来了；**crash buffer 0 行**说明没有任何崩溃。
这正是原始 bug 的反面：修复前走不到 `Displayed` 就被 `ClassNotFoundException` 带走了。

### 7.3 最终全绿（run 37121121961 / `aa0a799`）

两个 job 全部 success：

```
JOB Build debug APK:               completed/success  (11:54:05Z -> 11:55:17Z)
JOB Launch smoke test (emulator):  completed/success  (11:55:21Z -> 11:57:40Z)
```

编码体检：

```
==== 源码编码体检 (BOM / 合法 UTF-8 / U+FFFD) ====
  检查了 40 个文本文件
  [ok] 全部是无 BOM 的合法 UTF-8，且不含替换字符
==== 编码体检通过 ====
```

APK 完整性校验（8 项全过）：

```
==== 0. 定位工具与产物 ====       [ok] APK 存在 (24440500 字节)
==== 1. ZIP 容器完整性 ====        [ok] ZIP 结构完整, 无 CRC 错误 / 截断
==== 2. aapt2 dump badging ====    [ok] package 名 = com.apertus.music
                                   [ok] launcher activity = com.apertus.music.MainActivity
                                   [ok] 声明了 INTERNET 权限
==== 3. 明文 manifest ====         [ok] apkanalyzer manifest print 成功
==== 4. apk summary ====           [ok] apkanalyzer 报告的包名一致
==== 5. 对齐 ====                  [ok] zipalign 校验通过
==== 6. 签名 ====                  [ok] 签名有效
==== 7. apkcheck.py ====           条目 136 (STORED 75)  native-lib 4
                                   (13 个 dex 全部 dex 037  header=0x70)
                                   manifest 点名的 5 个组件类, DEX 里全部存在
                                   DEX 里共 37077 个类
                                   PASS  composeApp-debug.apk
                                   [ok] apkcheck.py 通过
==== 8. dex packages 交叉确认 ==== [ok] dex 里确实定义了 com.apertus.music.MainActivity
==== 结果 ====
sha256=3f2f11559cdff7faae301ee3a5492dc7e1ab1614d40dc847c9e6db18584b05cf
result=PASS
APK VERIFICATION PASSED: 容器 / dex / 对齐 / 签名 / 清单引用全部通过。
```

模拟器冒烟：

```
== Installing                                        Success
== Launcher activity resolved from AndroidManifest.xml
resolved: 'com.apertus.music/com.apertus.music.MainActivity'   (adb 原始输出: 'com.apertus.music/.MainActivity')
expected: 'com.apertus.music/com.apertus.music.MainActivity'
== Launching via LAUNCHER intent (monkey)
Events injected: 1
    topResumedActivity=ActivityRecord{cb8926 u0 com.apertus.music/.MainActivity t8}
pid: '3091'
== main log: 33580 lines | crash buffer: 0 lines
Displayed com.apertus.music/.MainActivity for user 0: +4s559ms
SMOKE TEST PASSED: com.apertus.music resolved to com.apertus.music/com.apertus.music.MainActivity,
                   launched, and is still running (pid 3091).
```

注意这里 `resolved:` 一行同时打印了 adb 的**原始**输出（`com.apertus.music/.MainActivity`，
短格式）和规范化后的结果 —— 这正是 §5.8 那次误报留下的痕迹，留着便于以后排查。

### 7.4 最终全绿：单测 + 性能闸门（run 37123239623 / `7832ae5`）

两个 job 全部 success：

```
JOB Build debug APK:                                  completed/success  (12:33:31Z -> 12:35:01Z)
JOB Launch smoke test + performance gate (emulator):  completed/success  (12:35:05Z -> 12:37:49Z)
```

单元测试（`desktopTest`，26 个用例，来自 `build-reports` 产物里的 HTML 报告）：

```
tests: 26   failures: 0   success: 100%
```

APK 完整性校验 —— 8 项全过。注意 APK 从 24440500 字节降到 **17525969 字节**，
因为 `material-icons-extended` 被自绘图标集取代后整包小掉了约 7 MB：

```
[ok] APK 存在 (17525969 字节)
[ok] ZIP 结构完整, 无 CRC 错误 / 截断
[ok] package 名 = com.apertus.music
[ok] launcher activity = com.apertus.music.MainActivity
[ok] 声明了 INTERNET 权限
[ok] apkanalyzer manifest print 成功 (manifest 可解析)
[ok] apkanalyzer 报告的包名一致
[ok] zipalign 校验通过
[ok] 签名有效
manifest 点名的 5 个组件类, DEX 里全部存在
[ok] apkcheck.py 通过
[ok] dex 里确实定义了 com.apertus.music.MainActivity
sha256=b205c8e0dec0344400be63ad91382ba0d1fe97a189493e09cdb7a9f718ffea02
APK VERIFICATION PASSED: 容器 / dex / 对齐 / 签名 / 清单引用全部通过。
```

源码编码体检：

```
检查了 50 个文本文件
[ok] 全部是无 BOM 的合法 UTF-8，且不含替换字符
```

模拟器冒烟：

```
resolved: 'com.apertus.music/com.apertus.music.MainActivity'   (adb 原始输出: 'com.apertus.music/.MainActivity')
Events injected: 1
topResumedActivity=ActivityRecord{3000675 u0 com.apertus.music/.MainActivity t8}
pid: '3221'
== main log: 37775 lines | crash buffer: 0 lines
Displayed com.apertus.music/.MainActivity for user 0: +5s548ms
SMOKE TEST PASSED
```

性能闸门（同一台 swiftshader 模拟器，每项 3 次取样取最好）：

```
== 0. Preconditions
[ok] platform animation scales restored to 1.0 for the measurement
[ok] log buffers cleared; only this run's events are judged
== 1. Cold start   cold start: 1584 ms  (budget 9000 ms)     [ok]
== 2. Warm start   warm start:  163 ms   (budget 3000 ms)     [ok]
== 3. Memory       total PSS: 103485 kB  (budget 614400 kB)   [ok]
== 4. Liveness     resumed: topResumedActivity=...com.apertus.music/.MainActivity
                   app pid: 5114
                   [ok] no ANR recorded for com.apertus.music
                   [ok] no fatal exception in com.apertus.music during the run
result=PASS
PERFORMANCE GATE PASSED: cold 1584 ms, warm 163 ms, PSS 103485 kB.
```

第 5 节 `gfxinfo` 只做展示、不进预算：模拟器用 swiftshader 软件渲染，
里面 `Janky frames: 14 (63.64%)`、`95th percentile: 1300ms` 反映的是**模拟器的
渲染速度**而不是 App 的开销，所以脚本只打印、不判定。真正的回归保护由 §10 的
预算表和 JVM 上的 `PerformanceTest.kt` 承担。

---

## 8. 跳转要等 2 秒：原因与修法

反馈是「跳转进度条要整整 2s 才有反应」。查下来是**三个问题叠在一起**，缺一个都不会
这么明显：

1. `ui/PlayerScreen.kt` 的 `Slider(value = progress)` 直接绑在 300 ms 轮询出来的位置上，
   拖动过程中**没有任何本地状态** —— 于是下一次重组就把拇指拽回旧位置，看起来像「拖不动」。
2. `onValueChange` 每动一下就 `playerStore.seekTo(...)`。一次拖动会发出**几十次** ExoPlayer
   seek，每次 seek 都会触发一次重新缓冲。
3. 松手之后，轮询在重新缓冲期间读到的 `currentPosition()` 还是**旧值**，拇指于是又跳回去一次。

修法（三层，缺一层都还会回弹）：

| 层 | 文件 | 做了什么 |
|---|---|---|
| 手势 | `ui/PlayerScreen.kt` | 拖动期间只写本地 `var scrubFraction`，**不碰播放器**；`onValueChangeFinished` 里发**恰好一次** seek，然后清掉 |
| 状态 | `state/PlayerStore.kt` | `seekTo(target)` 先把 `_position.value = target` 写进去，再通知 controller —— UI 立刻看到新位置，不用等后端 |
| 后端 | `player/SeekGate.kt` + `player/GadulkaPlayerController.kt` | 跳转后由闸门接管位置：轮询报回来的值与目标**在 400 ms 容差内**才认；**超过 1500 ms** 直接认命（万一 seek 根本没落地，进度条也不会永远冻住） |

`SeekGate` 是纯逻辑、时钟可注入，所以能单测。回归测试是
`composeApp/src/commonTest/kotlin/com/apertus/music/state/PlayerStoreTest.kt` 里的
`aSeekIsVisibleBeforeTheBackendReportsIt`：`seekTo(42_000)` 之后，即使假后端
仍然报 0，`position` 也必须是 42_000 且保持不变。

## 9. 界面：MD3 Expressive、动效与自绘图标

### 9.1 MD3 Expressive 在 Compose Multiplatform 1.9.0 里到底有什么

`compose.material3` 访问器实际发出的坐标是 `org.jetbrains.compose.material3:material3:1.9.0`
（不是版本目录里写的 1.12.1 —— 那批别名是死的，见 §5.5）。为了不靠猜，我把这个版本的
`sources.jar` 从 Maven Central 下下来，**在真实源码里逐个查符号**：

| 符号 | 结果 |
|---|---|
| `MaterialExpressiveTheme` | 存在但是 **`internal`** —— App 侧根本调不到 |
| `MotionScheme` / `MotionScheme.expressive()` / `.standard()` | 同样 **`internal`** |
| 公开的 `MaterialTheme(...)` | 只有 `colorScheme / shapes / typography / content` 四个参数，**没有 `motionScheme`**；带 `motionScheme` 的那个重载是 `internal` |
| `MaterialShapes` | **不存在** |
| `LoadingIndicator` / `ContainedLoadingIndicator` | **没有公开函数**（只有 `tokens/LoadingIndicatorTokens.kt`） |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` / `FloatingToolbarDefaults` | **不存在** |
| `FloatingActionButtonMenu` / `ToggleFloatingActionButton` | **不存在** |
| `SplitButtonLayout` | **不存在** |
| `ButtonGroup` | **没有公开 API**（只有 token 文件） |
| 带 `thumbTrackGapSize` / `trackInsideCornerSize` 的 `Slider` 重载 | **`internal`**；公开的 `Slider` 只有 `value / onValueChange / modifier / enabled / valueRange / steps / onValueChangeFinished / colors / interactionSource` |
| `WideNavigationRail` / `WideNavigationRailItem` / `WideNavigationRailState` / `WideNavigationRailValue` / `rememberWideNavigationRailState` / `ModalWideNavigationRail` | **公开可用** |
| `ShortNavigationBar` / `ShortNavigationBarItem` | **公开可用** |

结论：Expressive 的**主题与动效方案**在 CMP 1.9.0 里还没有公开 API，所以主题继续由
`MaterialTheme` 承载自己那套 palette / shapes / typography，而不是去调一个调不到的
`MaterialExpressiveTheme`。但 Expressive 的**导航组件**是公开的，所以直接用上了：

- 宽屏用 `WideNavigationRail`（`state = rememberWideNavigationRailState(initialValue = Expanded)`，
  头部图标可点击在展开/收起之间切换动画）；
- 窄屏用 `ShortNavigationBar`。

这两个替换掉了原来的 `NavigationRail` / `NavigationBar`。

### 9.2 配色是量过的

`theme/AppTheme.kt` 里是一套手写的 Apertus 品牌色。浅色 / 深色两套 `ColorScheme` 的
每一组前景-背景都跑过 WCAG 对比度计算，**全部 ≥ 4.5:1**（正文门槛），实测最紧的一组
是 5.88:1。

没有用 Android 动态取色：动态色会跟着用户壁纸走，两个用户看到的 Apertus 不是同一个
Apertus。这个 App 需要自己的辨识度。

`Shapes` 从 8dp 到 36dp 递增；`Typography` 在 Material 3 默认字阶上把标题加粗、字距收紧。

### 9.3 动效

- 全屏播放器是 `AnimatedVisibility` 从底部滑入的**浮层**，而不是替换整个屏幕 ——
  曲库始终在下面保持着，返回时不需要重建列表、不需要重下封面。
- 曲库与设置之间用 `Crossfade`（240 ms）。
- 正在播放的那一行会轻微放大（1f ↔ 0.985f）并显示跳动的音柱。
- 迷你播放器有一条随进度平滑推进的细线。
- 播放/暂停图标用 `Crossfade` 切换，主按钮按下时缩放。

### 9.4 图标是自绘的

`theme/ApertusIcons.kt`：一组 24dp 的 `ImageVector`（`Play / Pause / SkipNext /
SkipPrevious / ArrowBack / Home / Tune / Library / Check / Equalizer / Logo`），
只用 `MoveTo / LineTo / Close`，填充一个纯色，由 Material 3 的 `Icon` 着色。

因为不再用任何 `Icons.Filled.*`，所以 `implementation(compose.materialIconsExtended)`
已经从 `composeApp/build.gradle.kts` 删掉了 —— 这个 artifact 被 Compose 插件钉在
**1.7.3**，上游明确说不会再更新。

## 10. 性能闸门

性能是一个指标，所以它进了 CI，而且**不过就红**。两层：

| 层 | 跑在哪 | 文件 | 挡住什么 |
|---|---|---|---|
| 热路径护栏 | JVM（`desktopTest`） | `composeApp/src/commonTest/kotlin/com/apertus/music/player/PerformanceTest.kt` | 有人把跳转路径写成线性扫描、每次轮询都分配一堆对象之类的**无界开销** |
| 真机指标 | Android 模拟器 | [`.github/scripts/perf-test.sh`](.github/scripts/perf-test.sh) | 冷启动 / 热启动 / 内存 / ANR 回归 |

预算（写在脚本顶部，改一个数就能调）：

| 指标 | 预算 | 取法 | 实测（run 37123239623） |
|---|---|---|---|
| 冷启动 `TotalTime` | ≤ 9000 ms | `am start -W`，先 force-stop | 1584 ms |
| 热启动 `TotalTime` | ≤ 3000 ms | 已在后台，按 HOME 再切回来 | 163 ms |
| 总 PSS | ≤ 614400 kB（600 MB） | `dumpsys meminfo` | 103485 kB |
| 进程存活 | 必须 | 顶部 Activity 还是我们，且 logcat 里没有 `ANR in` / `FATAL EXCEPTION` | 通过 |

三个刻意的设计决定：

1. **每项取 3 次里的最小值**，避免被模拟器抖动误伤，让闸门只在真实回归时红。
2. **脚本开头把 `window / transition / animator_duration_scale` 设回 1.0。** 冒烟任务用
   `disable-animations: true` 跑是刻意的（要确定性），但性能闸门不能这么干 ——
   否则量的是「动画全关」这个用户根本见不到的快版本。
3. **JVM 层故意把预算放得很宽（2000 ms 上限）**，它不是真的测性能，而是防止有人
   引入无界工作量；真正的数字由模拟器那层给。

输出写在 `perf/`（`summary.txt` / `meminfo.txt` / `logcat.txt` / `gfxinfo.txt`），
随 artifact `emulator-performance` 上传，成功失败都传。

## 11. 怎么拿 APK

```bash
gh run list --repo Maicy0609/Apertus-with-md3
gh run download <run-id> --repo Maicy0609/Apertus-with-md3 --name apertus-debug-apk
```

或网页：Actions → 某次运行 → Artifacts → `apertus-debug-apk`。
也可以 Actions → Android CI → Run workflow 手动触发。

每次运行还会上传 `build-reports`（含 `verify/` 下的全部校验原始输出）和
`emulator-logcat`（冒烟的 logcat 与 crash buffer）。

---

## 12. 已知遗留 / 后续建议

1. **API 还是占位符**：`commonMain/.../data/MusicApi.kt` 里
   `const val API_BASE_URL = "https://example.com/api"`，而 `App.kt` 用的是
   `FakeMusicRepository()`（4 首 SoundHelix 示例曲）。要接真实后端，改 Base URL 并把
   `FakeMusicRepository()` 换成 `MusicRepository(KtorMusicApi(client, API_BASE_URL))`。
2. **只有 debug 签名**：没有 release keystore/签名配置。上架前需要加签名密钥，并用
   GitHub Secrets 注入。
3. **`iosApp/` 不存在**：`commonMain` / `iosMain` 已就绪（`MainViewController.kt` 是 Swift
   侧入口），但没有 Xcode 工程。
4. **版本目录里的死别名**：见 §5.5，建议删掉或改正。
5. **模拟器只能到 API 34**：没有 API 37 的 system image。等 Google 发布后可以提升。
6. **`ubuntu-latest` 将于 2026-10-19 迁移到 Ubuntu 26**。届时值得重跑一次确认
   SDK 路径探测和模拟器步骤仍然有效。
7. **`tools/apkcheck.py` 是面向 debug APK 写的**。将来引入 R8/资源压缩后，可以按
   `classrefcheck.py` 的思路再加一层"删掉的类有没有被别的类引用到"的检查。
8. **MD3 Expressive 的主题与动效方案目前还调不到**（见 §9.1）：CMP 1.9.0 里
   `MaterialExpressiveTheme` / `MotionScheme` / `MaterialShapes` 都还是 `internal`
   或不存在。等上游公开之后，可以直接把手写的 shapes / typography 换成官方的，
   动画 spec 也能改用官方的 spring 曲线。届时值得复查一次
   `org.jetbrains.compose.material3:material3` 的版本。
9. **性能闸门是在模拟器上量的**：真机一般更快，但模拟器抖动更大。如果哪天闸门
   莫名其妙变红，先看 artifact `emulator-performance` 里的 `summary.txt` 再决定
   是调预算还是把取值从"3 次取最小"改成"5 次取中位数"。
10. **`MaterialTheme` 目前没有 `motionScheme` 参数可传**（见 §9.1），所以本项目的
    动画时长 / 缓动都写在各自的组件里。等官方参数公开后可以集中到主题层。

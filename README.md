# GlassTabBar

仿 iOS 26 液态玻璃的 Jetpack Compose 浮动底栏。

- 胶囊底栏：底下页面实时模糊 + 透镜折射 + 提饱和度，边缘高光描边、外投影、内阴影，浅色 / 深色各一套。
- 选中块：平时是中性灰白小胶囊；按住浮起透明水滴（折射 + 色散），比例照 iOS 26 App Store 录屏（约 1.26 格宽、上下探出底栏）。强调色只出现在水滴盖住的地方，半盖住就半边变色；底下的图标不动。
- 手势：按下水滴立刻滑到那格，拖动跟手、每过一格轻震，松手吸附最近一格；点选时水滴「拉丝」过去（前沿快、后沿慢）。
- 页面下滚时底栏收窄只留图标；点当前格回调 `onReselect`（用来滚回顶部）。
- `glass` 0..1 调玻璃强弱，`haptics` 开关震动。

玻璃效果用 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 `io.github.kyant0:backdrop:1.0.6`。

## 要求

- minSdk 33（折射用 RuntimeShader，Android 13 起才有）
- compileSdk 36，Kotlin 2.2+，Compose BOM 2025.12+

## 引入

`settings.gradle.kts`：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

模块 `build.gradle.kts`：

```kotlin
implementation("com.github.Jianyuanxi:GlassTabBar:v1.1.0")
```

## 用法

```kotlin
val bg = Color(0xFFF2F2F7)
val backdrop = rememberGlassBackdrop(bg)          // 1. 给页面录一层
var tab by remember { mutableIntStateOf(0) }

Box(Modifier.fillMaxSize().background(bg)) {
    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        // 2. 页面内容；底部留出 导航条 + GlassTabBarDefaults.Height + Gap * 2
    }
    GlassTabBar(                                  // 3. 底栏浮在最上面
        tabs = listOf(
            TabItem("首页", Icons.Outlined.Home, Icons.Filled.Home),
            TabItem("设置", Icons.Outlined.Settings, Icons.Filled.Settings),
        ),
        selected = tab,
        backdrop = backdrop,
        minimized = false,
        modifier = Modifier.align(Alignment.BottomCenter),
    ) { tab = it }
}
```

可选参数：

| 参数 | 默认 | 说明 |
|---|---|---|
| `dark` | 跟随系统 | App 自己有深浅色开关时传进来 |
| `colors` | 选中系统绿、没选中系统灰 | `GlassTabBarDefaults.colors(selected = …, unselected = …)` |
| `glass` | 0.5 | 0..1，模糊 2+8g dp、折射 6+24g / 12+48g dp |
| `lift` | 0.dp | 底栏离屏幕底边再抬高多少，负的往下压；页面底部内边距要同样加上 |
| `pull` | 0.5 | 0..1，水滴边缘靠近旁边的字时朝它鼓出去的强度，0 = 不鼓 |
| `haptics` | true | 关掉后不震 |
| `onReselect` | 空 | 点当前格 |

`rememberGlassBackdrop(bg)` 的底色要和页面底色一致，否则玻璃里会透出空洞。下滚收起的写法见 `sample/`。

## 演示

```bash
./gradlew :sample:installDebug
```

## 许可

Apache-2.0

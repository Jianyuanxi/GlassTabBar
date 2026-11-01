package io.github.jianyuanxi.glasstabbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.roundToInt

/** 底栏一格。选中时换实心图标（iOS 的做法：没选中是描边，选中是实心 + 强调色）。 */
data class TabItem(val title: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/** 选中 / 没选中时图标和文字的颜色。 */
@Immutable
data class GlassTabBarColors(val selected: Color, val unselected: Color)

object GlassTabBarDefaults {
    /** 底栏高度（不含上下留白和系统导航条）。 */
    val Height = 64.dp
    /** 底栏上下留白。页面底部内边距 = 导航条高度 + Height + Gap * 2。 */
    val Gap = 12.dp
    internal val MiniHeight = 50.dp

    /** 默认配色：选中苹果系统绿，没选中系统灰。 */
    @Composable
    fun colors(
        dark: Boolean = isSystemInDarkTheme(),
        selected: Color = if (dark) Color(0xFF30D158) else Color(0xFF34C759),
        unselected: Color = if (dark) Color(0xFF8E8E93) else Color(0xFF8E8E93),
    ) = GlassTabBarColors(selected, unselected)
}

/**
 * 给页面录一层，底栏从这层取图做模糊和折射。用法：
 * ```
 * val backdrop = rememberGlassBackdrop(pageBackground)
 * Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { 页面 }
 * GlassTabBar(..., backdrop = backdrop, ...)
 * ```
 * [background] 要和页面底色一致，否则玻璃里会透出空洞。
 */
@Composable
fun rememberGlassBackdrop(background: Color): LayerBackdrop {
    val bg by rememberUpdatedState(background)
    return rememberLayerBackdrop {
        drawRect(bg)
        drawContent()
    }
}

/**
 * 浮起来的液态玻璃胶囊底栏。
 *
 * - 胶囊本身：底下的页面经模糊 + 折射透上来，边缘一圈高光，下面一层软阴影。
 * - 选中块：平时是一颗中性灰白的半透明小胶囊（参照 iOS 26 App Store，不用强调色）；手指一按下就浮起成透明水滴、滑到按着的那格，放大底下的图标；
 *   水滴按 App Store 录屏的比例（约 1.26 格宽、上下探出底栏），强调色只出现在水滴盖住的地方。
 *   拖动时跟着手指，每划过一格轻震一下，松手贴到最近的一格。点别的格也一样：水滴放大着「拉丝」过去
 *   （前沿快、后沿慢），快到了才缩回去贴合。
 * - 页面往下滚时底栏收窄变矮、只留图标（[minimized]），往回滚或切页时展开。
 * - 点当前这格 = [onReselect]（各页用来滚回顶部）。
 * - [glass] 0..1 调玻璃强弱（模糊和折射一起变），[lift] 把底栏往上抬（负的往下压），
 *   [pull] 0..1 是水滴边缘靠近旁边的字时朝它鼓出去的强度（0 = 不鼓），[haptics] 关掉后不震。
 * - [dark] 决定玻璃用深色还是浅色那套；App 自己有深浅色开关时传进来。
 */
@Composable
fun GlassTabBar(
    tabs: List<TabItem>,
    selected: Int,
    backdrop: LayerBackdrop,
    minimized: Boolean,
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    colors: GlassTabBarColors = GlassTabBarDefaults.colors(dark),
    glass: Float = 0.5f,
    lift: Dp = 0.dp,
    pull: Float = 0.5f,
    haptics: Boolean = true,
    onReselect: (Int) -> Unit = {},
    onPick: (Int) -> Unit,
) {
    val BarHeight = GlassTabBarDefaults.Height
    val BarGap = GlassTabBarDefaults.Gap
    val MiniHeight = GlassTabBarDefaults.MiniHeight
    val haptic = LocalHapticFeedback.current
    val buzz by rememberUpdatedState(haptics)
    fun tick(t: HapticFeedbackType) { if (buzz) haptic.performHapticFeedback(t) }
    val scope = rememberCoroutineScope()
    val n = tabs.size
    // 只铺一层很淡的雾，底下滚过的内容要透得出来（参照 iOS 26 的 App Store 底栏）
    val surface = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.24f) else Color.White.copy(alpha = 0.26f)
    // 选中块是一片浮在上面的灰白玻璃：浅色压一层淡灰，深色提一层淡白，边上一道细高光
    val pill = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f)
    val pillRim = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.7f)
    val rim = if (dark) listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))
    else listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.10f))

    // 另录一份全部是选中样式（实心 + 强调色）的图标层，不上屏，只给水滴取样：
    // 水滴盖到哪儿，哪儿的图标就变强调色，半盖着就半边变色（照 iOS 26 App Store）
    val accentLayer = rememberLayerBackdrop()
    val lensSource = rememberCombinedBackdrop(backdrop, accentLayer)

    // 选中块左右两条边，单位是「格」
    val left = remember { Animatable(selected.toFloat()) }
    val right = remember { Animatable(selected + 1f) }
    // 0 = 静止，1 = 按住（水滴浮起）
    val press = remember { Animatable(0f) }
    // 手指按下到水滴落定这段时间由手势自己驱动选中块，外面 selected 变了不另外滑
    var held by remember { mutableStateOf(false) }
    // 水滴外的图标显不显示选中色。水滴开始收回前就放开，让底下的图标和水滴里的同时是强调色，
    // 不然收回那一下会「强调色 → 灰 → 强调色」闪一次
    var lit by remember { mutableStateOf(true) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val lensOn by remember { derivedStateOf { press.value > 0.01f } }
    val mini by animateFloatAsState(if (minimized) 1f else 0f, spring(0.82f, 380f), label = "mini")

    val sel by rememberUpdatedState(selected)
    val pick by rememberUpdatedState(onPick)
    val again by rememberUpdatedState(onReselect)

    LaunchedEffect(selected) { if (!held) slide(left, right, selected) }

    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = lerp(20.dp, 64.dp, mini))
            .padding(top = BarGap, bottom = (BarGap + lift).coerceAtLeast(0.dp))
            .fillMaxWidth()
            .height(lerp(BarHeight, MiniHeight, mini)),
    ) {
        val cellDp = maxWidth / n
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur((2f + 8f * glass).dp.toPx())
                        lens((6f + 24f * glass).dp.toPx(), (12f + 48f * glass).dp.toPx())
                    },
                    highlight = { Highlight.Default },
                    // 纯色背景上折射和模糊看不出来，靠这三样让玻璃在任何背景上都有轮廓：
                    // 外面一圈软投影、里面一圈内阴影、边缘一道细描边（上亮下暗，像光从上面打下来）
                    shadow = {
                        Shadow.Default.copy(radius = 22.dp, offset = DpOffset(0.dp, 6.dp), color = Color.Black,
                            alpha = if (dark) 0.55f else 0.16f)
                    },
                    innerShadow = {
                        InnerShadow.Default.copy(radius = 10.dp, offset = DpOffset(0.dp, 2.dp),
                            color = if (dark) Color.Black else Color(0xFF6E6E73), alpha = if (dark) 0.45f else 0.16f)
                    },
                    // 按住时整条胶囊鼓起，主要往两边撑
                    layerBlock = {
                        val p = press.value
                        scaleX = 1f + 0.03f * p
                        scaleY = 1f + 0.02f * p
                    },
                    onDrawSurface = { drawRect(surface) },
                    onDrawFront = {
                        val w = 1.dp.toPx()
                        drawRoundRect(
                            Brush.verticalGradient(rim),
                            topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius((size.height - w) / 2), style = Stroke(w),
                        )
                    },
                )
                .pointerInput(n) {
                    awaitEachGesture {
                        val cell = size.width / n.toFloat()
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = (down.position.x / cell).toInt().coerceIn(0, n - 1)
                        // 按下就浮起水滴，并且立刻滑到手指按着的那格（不是停在原来选中的那格）
                        settle?.cancel()
                        held = true
                        lit = false
                        scope.launch { press.animateTo(1f, spring(0.55f, 600f)) }
                        scope.launch { slide(left, right, start) }
                        var drag = false
                        var hover = start
                        if (start != sel) tick(HapticFeedbackType.SegmentFrequentTick)
                        var up = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { up = true; break }
                            val dx = ch.position.x - down.position.x
                            if (!drag && abs(dx) > viewConfiguration.touchSlop) {
                                drag = true
                                tick(HapticFeedbackType.GestureThresholdActivate)
                            }
                            if (drag) {
                                // 拖动时水滴中心跟着手指
                                ch.consume()
                                val t = (ch.position.x / cell - 0.5f).coerceIn(-0.15f, n - 0.85f)
                                follow(scope, left, right, t)
                                val h = t.roundToInt().coerceIn(0, n - 1)
                                if (h != hover) {
                                    hover = h
                                    tick(HapticFeedbackType.SegmentFrequentTick)
                                }
                            }
                        }
                        val target = when {
                            !up -> sel                  // 被打断（比如系统手势）：回原位
                            drag -> hover               // 松手贴到最近的一格
                            else -> start
                        }
                        // 水滴保持放大滑到目标格，快到了才缩回去贴合
                        settle = scope.launch {
                            launch { slide(left, right, target) }
                            snapshotFlow { abs(left.value - target) < 0.12f && abs(right.value - target - 1f) < 0.12f }
                                .first { it }
                            lit = true
                            press.animateTo(0f, spring(0.6f, 380f))
                            held = false
                        }
                        when {
                            target != sel -> {
                                tick(HapticFeedbackType.SegmentTick)
                                pick(target)
                            }
                            up && !drag -> again(target)
                        }
                    }
                },
        ) {
            // 静止时的淡色选中块，画在图标下面；按住时淡出，让位给水滴
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val cw = cellDp.toPx()
                    val inset = 5.dp.toPx()
                    val (x0, x1) = span(left.value, right.value, cw, inset)
                    // 拉得越长越扁一点，像液体被拽开（从最左跳到最右也只扁一点点）
                    val stretch = ((x1 - x0) / (cw - 2 * inset) - 1f).coerceIn(0f, 1f)
                    val h = (size.height - 2 * inset) * (1f - 0.05f * stretch)
                    val y = (size.height - h) / 2
                    val k = 1f - press.value
                    drawRoundRect(pill.copy(alpha = pill.alpha * k), Offset(x0, y), Size(x1 - x0, h), CornerRadius(h / 2))
                    val w = 0.5.dp.toPx()
                    drawRoundRect(
                        pillRim.copy(alpha = pillRim.alpha * k), Offset(x0 + w / 2, y + w / 2), Size(x1 - x0 - w, h - w),
                        CornerRadius((h - w) / 2), style = Stroke(w),
                    )
                },
            )
            // 底下的图标按住时一动不动，只有水滴里的那份被放大、折射
            Row(Modifier.fillMaxSize().graphicsLayer { alpha = 0f }.layerBackdrop(accentLayer), horizontalArrangement = Arrangement.SpaceEvenly) {
                tabs.forEach { t -> TabCell(t, true, 1f - mini, colors, Modifier.weight(1f), ghost = true) {} }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                tabs.forEachIndexed { i, t ->
                    // 按住时强调色只留在水滴里，水滴外的图标都是普通色
                    TabCell(t, i == selected && lit, 1f - mini, colors, Modifier.weight(1f)) {
                        if (i == selected) again(i) else pick(i)
                    }
                }
            }
        }
        if (lensOn) {
            // 水滴：上下探出胶囊，透明玻璃 + 折射 + 色散 + 边缘高光。
            // 外形用连续曲率胶囊（两头不是正半圆）；按住时按 App Store 的比例（[DropCells]、[DropAspect]），
            // 滑动时被拉长、略变矮。
            Box(
                Modifier
                    .layout { m, cons ->
                        val cw = cons.maxWidth / n.toFloat()
                        val p = press.value
                        val grow = 5.dp.toPx() * p
                        val inset = 5.dp.toPx() * (1f - p)
                        val (a, b) = span(left.value, right.value, cw, inset)
                        val base = cw - 2 * inset
                        val extra = (b - a - base).coerceAtLeast(0f)
                        val h0 = (cons.maxHeight - 2 * inset + 2 * grow) * (1f - 0.15f * (extra / base).coerceIn(0f, 1f) * p)
                        // 静止时就是选中块的宽；浮起后过渡到 App Store 的比例 + 滑动拉长的部分
                        val full = minOf(DropCells * cw, h0 * DropAspect) + extra
                        val w0 = (b - a) + (full - (b - a)) * p
                        val mid = (a + b) / 2
                        // 边缘被旁边的字吸过去：哪侧的边快碰到图标，那侧的边就朝它鼓出去，碰上后松开。
                        // 只改外形，不动中心，拖动快慢照旧由手指决定
                        var reachL = 0f
                        var reachR = 0f
                        for (i in 0 until n) {
                            val dx = (i + 0.5f) * cw - mid
                            val f = reach((abs(dx) - w0 / 2) / cw)
                            if (dx > 0) reachR = maxOf(reachR, f) else reachL = maxOf(reachL, f)
                        }
                        val k = 0.25f * cw * pull * p
                        val x0 = mid - w0 / 2 - reachL * k
                        val x1 = mid + w0 / 2 + reachR * k
                        val w = x1 - x0
                        val h = h0 * (1f - 0.08f * maxOf(reachL, reachR) * pull * p)
                        val placeable = m.measure(Constraints.fixed(w.roundToInt(), h.roundToInt()))
                        layout(cons.maxWidth, cons.maxHeight) {
                            placeable.place(x0.roundToInt(), ((cons.maxHeight - h) / 2).roundToInt())
                        }
                    }
                    .drawBackdrop(
                        backdrop = lensSource,
                        shape = { DropShape },
                        effects = {
                            val p = press.value
                            lens(size.minDimension * 0.3f * p, 20.dp.toPx() * p, depthEffect = true, chromaticAberration = true)
                        },
                        highlight = { Highlight.Default.copy(alpha = press.value) },
                        shadow = { Shadow.Default.copy(alpha = 0.35f * press.value) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = (if (dark) 0.05f else 0.12f) * press.value)) },
                    ),
            )
        }
    }
}

/** 选中块左右边的像素位置；跳得远时两边拉开太多，按中心收到最多 1.5 格宽，免得变形太夸张 */
private fun span(left: Float, right: Float, cw: Float, inset: Float): Pair<Float, Float> {
    val mid = (left + right) / 2 * cw
    val half = ((right - left) * cw / 2 - inset).coerceIn(0f, cw * 0.75f - inset)
    return (mid - half) to (mid + half)
}

/** 点选：两条边各用一个弹簧，朝哪边走那边的边快、另一边慢，中途自然拉长成水滴状。 */
private suspend fun slide(left: Animatable<Float, *>, right: Animatable<Float, *>, to: Int) {
    val toRight = to > left.value
    // 刚度 290 / 165：跨一格约 350 ms 走完；520 / 300 实测太快
    val fast = spring<Float>(0.78f, 290f)
    val slow = spring<Float>(0.74f, 165f)
    kotlinx.coroutines.coroutineScope {
        launch { left.animateTo(to.toFloat(), if (toRight) slow else fast) }
        launch { right.animateTo(to + 1f, if (toRight) fast else slow) }
    }
}

private val DropShape = Capsule()
// 照 iOS 26 App Store 录屏量的：水滴约 1.26 格宽、宽高比约 1.85、上下各探出底栏约 8%；
// 格子宽（平板）时按宽高比封顶，免得拉成长条
private const val DropCells = 1.26f
private const val DropAspect = 1.85f

/**
 * 水滴一侧边缘被图标吸过去的程度（0..1），gap 是这侧边缘到图标中心的距离（格）。
 * 离 0.5 格以内开始鼓，约 0.12 格最强；图标进到水滴里面（gap < 0）就松开。
 */
private fun reach(gap: Float): Float {
    fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
    return smooth(0.5f, 0.12f, gap) * smooth(-0.12f, 0.04f, gap)
}

/** 拖动：跟手但带一点弹簧惯性，前沿比后沿紧，快速甩动时会拉长。 */
private fun follow(scope: CoroutineScope, left: Animatable<Float, *>, right: Animatable<Float, *>, t: Float) {
    val toRight = t > left.value
    val lead = spring<Float>(0.85f, 1400f)
    val trail = spring<Float>(0.8f, 520f)
    scope.launch { left.animateTo(t, if (toRight) trail else lead) }
    scope.launch { right.animateTo(t + 1f, if (toRight) lead else trail) }
}

@Composable
private fun TabCell(
    t: TabItem, on: Boolean, label: Float, colors: GlassTabBarColors, modifier: Modifier, ghost: Boolean = false,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (on) colors.selected else colors.unselected, label = "tint")
    val pop by animateFloatAsState(if (on) 1f else 0.92f, spring(0.42f, 420f), label = "pop")
    Column(
        modifier
            .fillMaxHeight()
            // 手势由整条栏统一处理（要支持拖动），这里只给读屏补上「标签页」语义；水滴取样用的那份不进读屏
            .then(
                if (ghost) Modifier else Modifier.semantics {
                    role = Role.Tab
                    selected = on
                    onClick(t.title) { onClick(); true }
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (on) t.selectedIcon else t.icon, null, tint = tint,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop },
        )
        if (label > 0.02f) {
            Text(
                t.title, color = tint, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                // 按文字实际高度收起（写死 14dp 会把中文字形下半截裁掉）
                modifier = Modifier.graphicsLayer { alpha = label }.layout { m, cons ->
                    val p = m.measure(cons.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    layout(p.width, (p.height * label).roundToInt()) { p.place(0, 0) }
                },
            )
        }
    }
}

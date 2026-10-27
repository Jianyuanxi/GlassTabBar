package io.github.jianyuanxi.glasstabbar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.jianyuanxi.glasstabbar.GlassTabBar
import io.github.jianyuanxi.glasstabbar.GlassTabBarDefaults
import io.github.jianyuanxi.glasstabbar.TabItem
import io.github.jianyuanxi.glasstabbar.rememberGlassBackdrop
import kotlinx.coroutines.launch

private val Tabs = listOf(
    TabItem("首页", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem("收藏", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    TabItem("我的", Icons.Outlined.Person, Icons.Filled.Person),
    TabItem("设置", Icons.Outlined.Settings, Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Demo() }
    }
}

@Composable
private fun Demo() {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color.Black else Color(0xFFF2F2F7)
    var tab by remember { mutableIntStateOf(0) }
    var mini by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab) { mini = false }

    // 1. 给页面录一层，底栏从这层取图
    val backdrop = rememberGlassBackdrop(bg)
    // 2. 往下滚收起底栏，往回滚展开
    val scrollWatch = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -6f) mini = true else if (consumed.y > 6f) mini = false
                return Offset.Zero
            }
        }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        GlassTabBarDefaults.Height + GlassTabBarDefaults.Gap * 2
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(bg)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            LazyColumn(
                Modifier.fillMaxSize().nestedScroll(scrollWatch),
                state = list,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom),
            ) {
                item {
                    Text(Tabs[tab].title, fontSize = 34.sp, color = if (dark) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 12.dp))
                }
                items(30) { i -> Card(i + tab * 3) }
            }
        }
        // 3. 底栏浮在最上面
        GlassTabBar(
            tabs = Tabs,
            selected = tab,
            backdrop = backdrop,
            minimized = mini,
            modifier = Modifier.align(Alignment.BottomCenter),
            onReselect = { mini = false; scope.launch { list.animateScrollToItem(0) } },
        ) { tab = it }
    }
}

private val Swatches = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF00C7BE), Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFFAF52DE),
)

/** 彩色卡片：玻璃的模糊、折射在彩色内容上才看得出来。 */
@Composable
private fun Card(i: Int) {
    Box(
        Modifier.padding(vertical = 6.dp).fillMaxWidth().height(96.dp)
            .clip(RoundedCornerShape(20.dp)).background(Swatches[i % Swatches.size]),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("卡片 ${i + 1}", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(start = 20.dp))
    }
}

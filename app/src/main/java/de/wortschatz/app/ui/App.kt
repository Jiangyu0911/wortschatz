package de.wortschatz.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wortschatz.app.AppViewModel

private data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

@Composable
fun WortschatzApp(vm: AppViewModel) {
    // 加载结束时不直接「啪」地切走，与主界面做一次淡入淡出
    Crossfade(
        targetState = vm.initializing,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "splash"
    ) { loading ->
        if (loading) Splash() else MainScaffold(vm)
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showStudy by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showRecords by remember { mutableStateOf(false) }
    var showBook by remember { mutableStateOf(false) }
    var showPhotoImport by remember { mutableStateOf(false) }
    var showTextImport by remember { mutableStateOf(false) }

    if (showTextImport) {
        TextImportScreen(
            vm = vm,
            onBack = { showTextImport = false },
            onLookup = { term ->
                vm.onQueryChange(term)
                showTextImport = false
                tab = 1
            }
        )
        return
    }

    if (showPhotoImport) {
        PhotoImportScreen(
            vm = vm,
            onBack = { showPhotoImport = false },
            onLookup = { term ->
                vm.onQueryChange(term)
                showPhotoImport = false
                tab = 1
            }
        )
        return
    }

    if (showBook) {
        BookDetailScreen(vm = vm, onBack = {
            showBook = false
            vm.refresh()
        })
        return
    }

    if (showRecords) {
        LearningRecordsScreen(vm = vm, onBack = {
            showRecords = false
            vm.refresh()
        })
        return
    }

    if (showCalendar) {
        CheckinCalendarScreen(vm = vm, onBack = { showCalendar = false })
        return
    }

    if (showStudy) {
        StudyScreen(vm = vm, onExit = {
            showStudy = false
            vm.refresh()
        })
        return
    }

    val tabs = listOf(
        TabItem("首页", Icons.Rounded.Home, Icons.Outlined.Home),
        TabItem("查词", Icons.Rounded.Search, Icons.Outlined.Search),
        TabItem("收藏", Icons.Rounded.Star, Icons.Outlined.StarBorder),
        TabItem("设置", Icons.Rounded.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                imageVector = if (tab == index) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
            Crossfade(targetState = tab, label = "tab") { current ->
                when (current) {
                    0 -> HomeScreen(
                        stats = vm.stats,
                        levelCounts = vm.levelCounts,
                        totalWords = vm.totalWords,
                        activeLevels = vm.levels,
                        dailyTarget = vm.sessionSize,
                        onStartReview = {
                            vm.startSession(AppViewModel.SessionMode.REVIEW)
                            showStudy = true
                        },
                        onStartNew = {
                            vm.startSession(AppViewModel.SessionMode.NEW)
                            showStudy = true
                        },
                        onOpenCalendar = { showCalendar = true },
                        onOpenLevels = { tab = 3 },
                        onOpenPhotoImport = { showPhotoImport = true },
                        onOpenTextImport = { showTextImport = true },
                        onOpenBooks = { tab = 3 },
                        onOpenRecords = { showRecords = true }
                    )

                    1 -> SearchScreen(vm = vm)
                    2 -> StarredScreen(vm = vm)
                    else -> SettingsScreen(
                        vm = vm,
                        onOpenBook = { showBook = true }
                    )
                }
                }
            }
            // 收藏等操作的撤销条：贴在底部导航栏上方，占据布局不遮挡列表
            UndoBar(vm)
        }
    }
}

@Composable
private fun Splash() {
    // 与系统启动图、桌面图标同一套配色：深蓝底 + 白色 W + 琥珀色下划线，
    // 系统启动图交接过来时不会出现「白底闪一下再变蓝」的跳变
    val splashBg = Color(0xFF1B2A4A)
    val markColor = Color.White
    val accent = Color(0xFFF2C078)

    val logoScale = remember { Animatable(0.94f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleLift = remember { Animatable(12f) }
    val footAlpha = remember { Animatable(0f) }

    // 极轻微的呼吸感，避免画面完全静止
    val breathe = rememberInfiniteTransition(label = "breathe")
    val glow by breathe.animateFloat(
        initialValue = 0.97f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
        launch { delay(90); titleAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { delay(90); titleLift.animateTo(0f, tween(460, easing = FastOutSlowInEasing)) }
        launch { delay(260); footAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = splashBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // 与 ic_launcher_foreground 同款：白色 W + 琥珀短横线
            Column(
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale.value * glow
                    scaleY = logoScale.value * glow
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "W",
                    fontFamily = FontFamily.Default,
                    fontSize = 62.sp,
                    fontWeight = FontWeight.Bold,
                    color = markColor
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Wortschatz",
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleLift.value
                },
                fontFamily = FontFamily.Default,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = markColor
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "德 语 单 词",
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleLift.value
                },
                fontSize = 11.5.sp,
                letterSpacing = 3.sp,
                color = markColor.copy(alpha = 0.72f)
            )

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.graphicsLayer { alpha = footAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                    color = markColor.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "正在准备离线词库…",
                    fontSize = 11.sp,
                    color = markColor.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(34.dp))
            }
        }
    }
}

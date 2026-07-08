package com.d4nzxml.kythera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4nzxml.kythera.ui.screen.*
import com.d4nzxml.kythera.ui.theme.KColor
import com.d4nzxml.kythera.ui.theme.KytheraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KytheraTheme {
                KytheraShell()
            }
        }
    }
}

// ─── Navigation items ─────────────────────────────────────────────────────────
data class NavItem(val icon: ImageVector, val label: String)

val bottomNavItems = listOf(
    NavItem(Icons.Rounded.GridView,    "Dashboard"),
    NavItem(Icons.Rounded.SwapHoriz,   "Convert"),
    NavItem(Icons.Rounded.Compress,    "Compress"),
    NavItem(Icons.Rounded.Edit,        "Patch"),
    NavItem(Icons.Rounded.History,     "History"),
)

val drawerItems = listOf(
    Triple(Icons.Rounded.GridView,      "Dashboard",   0),
    Triple(Icons.Rounded.SwapHoriz,     "Converter",   1),
    Triple(Icons.Rounded.Compress,      "Compress",    2),
    Triple(Icons.Rounded.Edit,          "Patch Video", 3),
    Triple(Icons.Rounded.History,       "History",     4),
    Triple(Icons.Rounded.Image,         "Photo Enhance", 5),
    Triple(Icons.Rounded.Settings,      "Pengaturan",  6),
)

// ─── Shell ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KytheraShell() {
    var currentIndex by remember { mutableStateOf(0) }
    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            KytheraDrawer(
                currentIndex = currentIndex,
                onNavigate = { idx ->
                    currentIndex = idx
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = KColor.Bg,
            topBar = {
                KytheraAppBar(
                    currentIndex = currentIndex,
                    onMenuTap = { scope.launch { drawerState.open() } }
                )
            },
            bottomBar = {
                KytheraBottomNav(
                    currentIndex = if (currentIndex > 4) -1 else currentIndex,
                    onTap = { currentIndex = it }
                )
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "screen"
                ) { idx ->
                    when (idx) {
                        0    -> DashboardScreen(onNavigate = { currentIndex = it })
                        1    -> ConverterScreen()
                        2    -> CompressScreen()
                        3    -> PatchScreen()
                        4    -> HistoryScreen()
                        5    -> EnhanceScreen()
                        6    -> SettingsScreen()
                        else -> DashboardScreen(onNavigate = { currentIndex = it })
                    }
                }
            }
        }
    }
}

// ─── App Bar ──────────────────────────────────────────────────────────────────
@Composable
fun KytheraAppBar(currentIndex: Int, onMenuTap: () -> Unit) {
    val titles = listOf("Dashboard", "Converter", "Compress", "Patch", "History", "Photo Enhance", "Pengaturan")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KColor.Surface)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(KColor.Accent, KColor.Accent2)
                    )
                ),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Bolt, null, tint = Color.Black, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text("Kythera", color = KColor.Text, fontWeight = FontWeight.W800, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Text(
            titles.getOrElse(currentIndex) { "" },
            color = KColor.Text3, fontSize = 12.sp
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(KColor.Accent3)
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Rounded.Menu, null,
            tint = KColor.Text2, modifier = Modifier.size(22.dp).clickable(onClick = onMenuTap)
        )
    }
}

// ─── Bottom Nav ───────────────────────────────────────────────────────────────
@Composable
fun KytheraBottomNav(currentIndex: Int, onTap: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KColor.Surface)
            .border(1.dp, KColor.Border,
                shape = androidx.compose.ui.graphics.RectangleShape)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        bottomNavItems.forEachIndexed { i, item ->
            val isActive = currentIndex == i
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) KColor.Accent.copy(0.1f) else Color.Transparent)
                    .clickable { onTap(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, null,
                        tint = if (isActive) KColor.Accent else KColor.Text3,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.label,
                        color = if (isActive) KColor.Accent else KColor.Text3,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.W600 else FontWeight.W400)
                }
            }
        }
    }
}

// ─── Drawer ───────────────────────────────────────────────────────────────────
@Composable
fun KytheraDrawer(currentIndex: Int, onNavigate: (Int) -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = KColor.Surface,
        modifier = Modifier.width(280.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = KColor.Border,
                    shape = androidx.compose.ui.graphics.RectangleShape
                )
                .padding(20.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(KColor.Accent, KColor.Accent2)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Bolt, null, tint = Color.Black, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.height(12.dp))
            Text("Kythera Tools", color = KColor.Text, fontWeight = FontWeight.W800, fontSize = 18.sp)
            Text("Developer by D4nzxml", color = KColor.Text3, fontSize = 11.sp)
        }

        Spacer(Modifier.height(8.dp))
        drawerItems.forEach { (icon, label, index) ->
            val isActive = currentIndex == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) KColor.Accent.copy(0.1f) else Color.Transparent)
                    .run {
                        if (isActive) border(1.dp, KColor.Accent.copy(0.2f), RoundedCornerShape(10.dp))
                        else this
                    }
                    .clickable { onNavigate(index) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null,
                    tint = if (isActive) KColor.Accent else KColor.Text2,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(label,
                    color = if (isActive) KColor.Accent else KColor.Text2,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.W600 else FontWeight.W400)
            }
        }

        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth()
                .border(1.dp, KColor.Border, androidx.compose.ui.graphics.RectangleShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("v1.0.0 · FFmpeg min-gpl 6.x",
                color = KColor.Text3, fontSize = 10.sp)
        }
    }
}

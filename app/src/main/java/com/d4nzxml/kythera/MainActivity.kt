package com.d4nzxml.kythera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
                // Tambahin state penahan ini
                var isAuthenticated by remember { mutableStateOf(false) }

                if (!isAuthenticated) {
                    // Kalau belum verifikasi, kurung di layar ini
                    TelegramAuthScreen(
                        onVerifySuccess = { 
                            isAuthenticated = true 
                        }
                    )
                                } else {
                    // Kalau udah sukses, baru lepas ke Dashboard utama
                    KytheraShell()
                }
            } // Ini baris 61 di layar lu sekarang (Penutup KytheraTheme)
        } // 🔥 TAMBAHIN INI (Penutup setContent)
    } // 🔥 TAMBAHIN INI (Penutup onCreate)
} // 🔥 TAMBAHIN INI (Penutup class MainActivity)

//Navigation items

data class NavItem(val icon: ImageVector, val label: String)

// 🔥 Menu History diganti jadi Profile (Pakai Icon Standar Person)
val bottomNavItems = listOf(
    NavItem(Icons.Rounded.GridView,    "Dashboard"),
    NavItem(Icons.Rounded.SwapHoriz,   "Convert"),
    NavItem(Icons.Rounded.Compress,    "Compress"),
    NavItem(Icons.Rounded.Person,      "Profile") 
)

val drawerItems = listOf(
    Triple(Icons.Rounded.GridView,      "Dashboard",     0),
    Triple(Icons.Rounded.SwapHoriz,     "Converter",     1),
    Triple(Icons.Rounded.Compress,      "Compress",      2),
    Triple(Icons.Rounded.Person,        "Profile",       3), 
    Triple(Icons.Rounded.Image,         "Photo Enhance", 4),
    Triple(Icons.Rounded.Movie,         "Video Enhance", 5),
    Triple(Icons.Rounded.CloudUpload,   "Upload TikTok", 6),
    Triple(Icons.Rounded.Settings,      "Pengaturan",    7),
)

// ─── Shell ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KytheraShell() {
    var currentIndex by remember { mutableStateOf(0) }
    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current

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
                    currentIndex = if (currentIndex > 3) -1 else currentIndex,
                    onTap = { currentIndex = it }
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = currentIndex == 0,
                    enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(150)) + fadeOut(tween(150))
                ) {
                    FloatingActionButton(
                        onClick = { currentIndex = 6 },
                        containerColor = KColor.Accent,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = "Upload TikTok")
                    }
                }
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
                        3    -> HistoryScreen() // Tetap arahin ke file ini buat sementara, lu bisa rombak isinya nanti
                        4    -> EnhanceScreen()
                        5    -> VideoEnhanceScreen()
                        6    -> TikTokScreen()
                        7    -> SettingsScreen()
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
    val titles = listOf("Dashboard", "Converter", "Compress", "Profile", "Photo Enhance", "Video Enhance", "TikTok Upload", "Pengaturan")
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
        Text(titles.getOrElse(currentIndex) { "" }, color = KColor.Text3, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(KColor.Accent3))
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.Menu, null, tint = KColor.Text2, modifier = Modifier.size(22.dp).clickable(onClick = onMenuTap))
    }
}

// ─── Bottom Nav ───────────────────────────────────────────────────────────────
@Composable
fun KytheraBottomNav(currentIndex: Int, onTap: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KColor.Surface)
            .border(1.dp, KColor.Border, androidx.compose.ui.graphics.RectangleShape)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        bottomNavItems.forEachIndexed { i, item ->
            val isActive = currentIndex == i
            val interactionSource = remember { MutableInteractionSource() }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onTap(i) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.height(32.dp).padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isActive) KColor.Accent else KColor.Text3,
                        modifier = Modifier.size(if (isActive) 24.dp else 20.dp)
                    )
                }
                
                Text(
                    text = item.label,
                    color = if (isActive) KColor.Accent else KColor.Text3,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.W600 else FontWeight.W400
                )
                
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isActive) KColor.Accent else Color.Transparent)
                )
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
                .border(width = 1.dp, color = KColor.Border, shape = androidx.compose.ui.graphics.RectangleShape)
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
            Text("Powered by D4nzxml Studio", color = KColor.Text3, fontSize = 11.sp)
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
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(Icons.Rounded.Email, contentDescription = "Email", tint = KColor.Text3, modifier = Modifier.size(24.dp))
            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = KColor.Text3, modifier = Modifier.size(24.dp))
            Icon(Icons.Rounded.Language, contentDescription = "Web", tint = KColor.Text3, modifier = Modifier.size(24.dp))
        }

        Box(
            Modifier.fillMaxWidth()
                .border(1.dp, KColor.Border, androidx.compose.ui.graphics.RectangleShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("v2.0.1 · FFmpeg min-gpl 6.x", color = KColor.Text3, fontSize = 10.sp)
        }
    }
}

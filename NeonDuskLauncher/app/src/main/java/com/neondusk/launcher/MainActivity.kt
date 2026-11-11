package com.neondusk.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---- Palette ----
private val Cyan = Color(0xFF00E5FF)
private val Violet = Color(0xFF8B5CF6)
private val Ink = Color(0xFF080910)
private val TextC = Color(0xFFE8ECF4)

data class AppInfo(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: ImageBitmap
)

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var homeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet)) {
                LauncherRoot(apps, homeSignal)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            val list = loadApps(this@MainActivity)
            withContext(Dispatchers.Main) { apps = list }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal++
    }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(q, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { ri ->
            AppInfo(
                label = ri.loadLabel(pm).toString(),
                pkg = ri.activityInfo.packageName,
                component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                icon = ri.loadIcon(pm).toBitmap(144, 144).asImageBitmap()
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, app: AppInfo) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { ctx.startActivity(i) }
}

private val defaultPinned = listOf(
    "com.google.android.dialer",
    "com.google.android.apps.messaging",
    "com.android.chrome",
    "com.google.android.GoogleCamera",
    "com.google.android.gm",
    "com.google.android.calendar",
    "com.google.android.keep",
    "com.google.android.apps.maps"
)

@Composable
fun LauncherRoot(apps: List<AppInfo>, homeSignal: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    val pinned = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString("pinned", null)
            if (saved == null) addAll(defaultPinned)
            else addAll(saved.split(",").filter { it.isNotEmpty() })
        }
    }
    fun togglePin(pkg: String) {
        if (pkg in pinned) pinned.remove(pkg) else pinned.add(pkg)
        prefs.edit().putString("pinned", pinned.joinToString(",")).apply()
    }

    var drawer by remember { mutableStateOf(false) }
    LaunchedEffect(homeSignal) { drawer = false }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(apps, pinned, ::togglePin) { drawer = true }
        AnimatedVisibility(
            visible = drawer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Drawer(apps, pinned, ::togglePin) { drawer = false }
        }
    }
}

@Composable
fun HomeScreen(
    apps: List<AppInfo>,
    pinned: List<String>,
    onTogglePin: (String) -> Unit,
    openDrawer: () -> Unit
) {
    val pinnedApps = pinned.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99080910))))
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) openDrawer() },
                    onVerticalDrag = { _, d -> total += d }
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val wide = maxWidth >= 600.dp   // inner (unfolded) display
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    ClockBlock(size = 120)
                }
                Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    PinnedGrid(pinnedApps, cols = 4, onTogglePin = onTogglePin)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(48.dp))
                ClockBlock(size = 88)
                Spacer(Modifier.weight(1f))
                PinnedGrid(pinnedApps, cols = 4, onTogglePin = onTogglePin)
                Spacer(Modifier.height(24.dp))
            }
        }
        Text(
            "swipe up for apps",
            color = TextC.copy(alpha = 0.35f),
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun ClockBlock(size: Int) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            val sec = Calendar.getInstance().get(Calendar.SECOND)
            delay((60 - sec) * 1000L)
        }
    }
    val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    Column {
        Text(
            SimpleDateFormat(pattern, Locale.getDefault()).format(now),
            color = TextC, fontSize = size.sp, fontWeight = FontWeight.Thin, lineHeight = size.sp
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now).uppercase(),
            color = Cyan, fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PinnedGrid(items: List<AppInfo>, cols: Int, onTogglePin: (String) -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x22FFFFFF))
            .padding(8.dp)
    ) {
        if (items.isEmpty()) {
            Text(
                "Long-press any app in the drawer to pin it here.",
                color = TextC.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(16.dp)
            )
        }
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    AppIcon(app, pinned = true, onTogglePin = { onTogglePin(app.pkg) }, modifier = Modifier.weight(1f))
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunched: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = { launchApp(ctx, app); onLaunched() },
                    onLongClick = { menu = true }
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(app.icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(4.dp))
            Text(app.label, color = TextC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (pinned) "Unpin from home" else "Pin to home") },
                onClick = { menu = false; onTogglePin() }
            )
            DropdownMenuItem(
                text = { Text("App info") },
                onClick = {
                    menu = false
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Uninstall") },
                onClick = {
                    menu = false
                    ctx.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }
    }
}

@Composable
fun Drawer(
    apps: List<AppInfo>,
    pinned: List<String>,
    onTogglePin: (String) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val list = remember(q, apps) {
        if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), ignoreCase = true) }
    }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2080910))
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedTextColor = TextC,
                unfocusedTextColor = TextC,
                cursorColor = Cyan,
                focusedPlaceholderColor = TextC.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = TextC.copy(alpha = 0.5f)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                list.firstOrNull()?.let { launchApp(ctx, it); onClose() }
            })
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(list, key = { it.component.flattenToString() }) { app ->
                AppIcon(
                    app,
                    pinned = app.pkg in pinned,
                    onTogglePin = { onTogglePin(app.pkg) },
                    onLaunched = onClose
                )
            }
        }
    }
}

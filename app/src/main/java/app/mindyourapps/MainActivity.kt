package app.mindyourapps

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

private sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data object Settings : Screen
    data class Detail(val pkg: String) : Screen
}

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the app re-checks whether the accessibility switch is on. */
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Root(resumes.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF9DB8FF), secondary = Color(0xFFC6B8FF))
        else -> lightColorScheme(primary = Color(0xFF2F6BEA), secondary = Color(0xFF7C6BD6))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun Root(resumeTick: Int) {
    if (!Store.onboarded) {
        Onboarding(resumeTick)
    } else {
        MainNav(resumeTick)
    }
}

@Composable
private fun MainNav(resumeTick: Int) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            resumeTick,
            onAdd = { screen = Screen.Picker },
            onOpen = { screen = Screen.Detail(it) },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Picker -> PickerScreen(onboarding = false, onDone = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
        is Screen.Detail -> DetailScreen(s.pkg, onBack = { screen = Screen.Home })
    }
}

// ---------------------------------------------------------------------- shared bits

@Composable
private fun AppIcon(pkg: String, size: Int = 44) {
    val pm = LocalContext.current.packageManager
    val entry = remember(pkg) { AppCache.get(pm, pkg) }
    if (entry.icon != null) {
        Image(
            painter = BitmapPainter(entry.icon),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp))
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun appLabel(pkg: String): String {
    val pm = LocalContext.current.packageManager
    return remember(pkg) { AppCache.get(pm, pkg).label }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = fg, fontSize = 14.sp) }
}

// ---------------------------------------------------------------------- first launch

/** Step 1: pick apps. Step 2: turn it on. Then a short "you're all set". */
@Composable
private fun Onboarding(resumeTick: Int) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val serviceOn = remember(resumeTick) { MindService.isEnabled(ctx) }

    // Coming back from Android's settings with the switch on moves things along by itself.
    LaunchedEffect(serviceOn, step) {
        if (step == 1 && serviceOn) step = 2
    }

    when (step) {
        0 -> PickerScreen(onboarding = true, onDone = { step = if (serviceOn) 2 else 1 })
        1 -> EnableStep(resumeTick, onBack = { step = 0 })
        else -> DoneStep()
    }
}

@Composable
private fun EnableStep(resumeTick: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Step 2 of 2", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Turn it on", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text(
            "In the list that opens, find Mind Your Apps and switch it on. " +
                "It only notices which app opens. It never reads your screen, and nothing leaves your phone.",
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        EnableActions(resumeTick)
    }
}

@Composable
private fun DoneStep() {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("You're all set", fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Open any app you picked and Mind Your Apps will check in with you first.",
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = { MindService.instance?.showPreview() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("See what it looks like") }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { Store.finishOnboarding() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start") }
    }
}

/**
 * The button that opens Android's accessibility settings. The "restricted settings" help only
 * appears if the person came back and the switch is still off, so most people never see it.
 */
@Composable
private fun EnableActions(resumeTick: Int) {
    val ctx = LocalContext.current
    var launchedAt by remember { mutableIntStateOf(-1) }
    val cameBackStillOff = launchedAt >= 0 && resumeTick > launchedAt

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                launchedAt = resumeTick
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Open accessibility settings") }

        if (cameBackStillOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                "Still off? On Android 13 and newer the switch can be greyed out for apps installed " +
                    "outside the Play Store. Tap \"App info\" below, open the ⋮ menu at the top right, " +
                    "choose \"Allow restricted settings\", then try again.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("App info") }
        }
    }
}

// ---------------------------------------------------------------------- home

@Composable
private fun HomeScreen(
    resumeTick: Int,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    // Re-read the stats every few seconds while the screen is open.
    val tick by produceState(0) {
        while (true) {
            delay(5_000)
            value++
        }
    }
    val serviceOn = remember(resumeTick) { MindService.isEnabled(ctx) }

    Scaffold(
        topBar = {
            Bar("Mind Your Apps", null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add an app")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (serviceOn) StatusRow() else OffCard(resumeTick)
            }

            if (Store.apps.isEmpty()) {
                item { EmptyState(onAdd) }
            } else {
                item { OverviewCard(tick) }
                item {
                    Text(
                        "Your apps",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                items(Store.apps, key = { it }) { pkg ->
                    AppRow(pkg, tick, onOpen)
                }
            }
        }
    }
}

@Composable
private fun StatusRow() {
    val n = Store.apps.count { Store.isEnabled(it) }
    Row(
        Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (n == 0) "Active" else "Active · minding $n ${if (n == 1) "app" else "apps"}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OffCard(resumeTick: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Mind Your Apps is off",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Turn it on so it can check in before your apps open.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            EnableActions(resumeTick)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nothing to mind yet", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Pick the apps you'd like a friendly pause for, and they'll check in with you before they open.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAdd) { Text("Choose apps") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(pkg: String, tick: Int, onOpen: (String) -> Unit) {
    val live = MindService.instance?.liveExtraMs(pkg) ?: 0L
    val stat = remember(pkg, tick) { Store.stats(pkg, Store.today()) }
    var menu by remember { mutableStateOf(false) }

    Box {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onOpen(pkg) }, onLongClick = { menu = true }),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(appColor(pkg)))
                Spacer(Modifier.width(12.dp))
                AppIcon(pkg)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        appLabel(pkg), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listStats(stat.opens, stat.ms + live),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = Store.isEnabled(pkg),
                    onCheckedChange = { Store.setEnabled(pkg, it) },
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Stop minding this app") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = {
                    menu = false
                    Store.removeApp(pkg)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------- picker

/** Apps people most often want a pause for. Only the ones that are installed are shown. */
private val SUGGESTED = listOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill",
    "com.google.android.youtube",
    "com.reddit.frontpage",
    "com.twitter.android",
    "com.facebook.katana",
    "com.snapchat.android",
    "com.instagram.barcelona",
    "com.pinterest",
    "tv.twitch.android.app",
    "com.netflix.mediaclient",
    "com.discord",
)

@Composable
private fun PickerScreen(onboarding: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = loadApps(ctx.packageManager, ctx.packageName).also { list -> list.forEach { AppCache.put(it) } }
    }
    var query by remember { mutableStateOf("") }
    if (!onboarding) BackHandler(onBack = onDone)
    val count = Store.apps.size

    Scaffold(
        topBar = { Bar(if (onboarding) "Pick your apps" else "Choose apps", if (onboarding) null else onDone) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(
                    onClick = onDone,
                    enabled = !onboarding || count > 0,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    val n = "$count ${if (count == 1) "app" else "apps"}"
                    Text(
                        when {
                            onboarding && count == 0 -> "Pick an app to continue"
                            onboarding -> "Continue · $n"
                            count == 0 -> "Done"
                            else -> "Done · $n"
                        }
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (onboarding) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Step 1 of 2", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Which apps pull you in the most?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading your apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val q = query.trim()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (q.isNotEmpty()) {
                        items(list.filter { it.label.contains(q, ignoreCase = true) }, key = { it.pkg }) { PickRow(it) }
                    } else {
                        val suggested = list.filter { it.pkg in SUGGESTED }.sortedBy { SUGGESTED.indexOf(it.pkg) }
                        val rest = list.filter { it.pkg !in SUGGESTED }
                        if (suggested.isNotEmpty()) {
                            item(key = "h-suggested") { PickHeader("Often minded") }
                            items(suggested, key = { it.pkg }) { PickRow(it) }
                            item(key = "h-all") { PickHeader("All apps") }
                        }
                        items(rest, key = { it.pkg }) { PickRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickHeader(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickRow(a: AppEntry) {
    val on = a.pkg in Store.apps
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (on) Store.removeApp(a.pkg) else Store.addApp(a.pkg) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (a.icon != null) {
            Image(BitmapPainter(a.icon), null, Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)))
        } else Spacer(Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Text(a.label, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    2.dp,
                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------- detail

@Composable
private fun DetailScreen(pkg: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val week = remember(pkg) { Store.week(pkg) }
    var confirmRemove by remember { mutableStateOf(false) }
    val label = appLabel(pkg)

    Scaffold(topBar = { Bar(label, onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (Store.isEnabled(pkg)) "Checking in before it opens" else "Paused",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = Store.isEnabled(pkg), onCheckedChange = { Store.setEnabled(pkg, it) })
                }
            }
            item {
                ChartCard(
                    "Opens, last 7 days",
                    week.map { it.day to it.opens.toFloat() },
                    week.sumOf { it.opens }.toString() + " total",
                    appColor(pkg),
                )
            }
            item {
                ChartCard(
                    "Minutes, last 7 days",
                    week.map { it.day to (it.ms / 60_000f) },
                    fmtMinutes(week.sumOf { it.ms }) + " total",
                    appColor(pkg),
                )
            }
            item {
                if (!confirmRemove) {
                    TextButton(onClick = { confirmRemove = true }) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop minding this app")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { Store.removeApp(pkg); onBack() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Yes, remove") }
                        OutlinedButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, data: List<Pair<Long, Float>>, summary: String, bar: Color) {
    val dim = bar.copy(alpha = 0.25f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val today = LocalDate.now().toEpochDay()
    val max = (data.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(summary, fontSize = 13.sp, color = label)
            }
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val n = data.size
                val gap = 10.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                data.forEachIndexed { i, (day, v) ->
                    val h = if (v <= 0f) 4.dp.toPx() else (v / max) * size.height
                    drawRoundRect(
                        color = if (v <= 0f) dim else bar,
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        alpha = if (day == today) 1f else 0.75f,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                data.forEach { (day, _) ->
                    val d = LocalDate.ofEpochDay(day).dayOfWeek
                        .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
                    Text(d, fontSize = 12.sp, color = label, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var more by remember { mutableStateOf(false) }
    var previewNote by remember { mutableStateOf(false) }

    Scaffold(topBar = { Bar("Settings", onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { SectionTitle("Reminder time") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "The button on the right of the check-in opens the app and reminds you after this long.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 20, 30, 45, 60).forEach { m ->
                            Chip(label = "$m min", selected = Store.defaultMin == m) {
                                Store.chooseDefaultMin(m)
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Look of the check-in") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PauseTheme.entries.forEach { t -> ThemeRow(t) }
                    Button(onClick = {
                        val s = MindService.instance
                        if (s != null) {
                            s.showPreview()
                            previewNote = false
                        } else previewNote = true
                    }) { Text("Preview") }
                    if (previewNote) {
                        Text(
                            "Turn Mind Your Apps on first (see the top of the home screen).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                TextButton(onClick = { more = !more }) { Text(if (more) "Less" else "More") }
            }

            if (more) {
                item { SectionTitle("Come-back window") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Switching back to an app within this time doesn't count as a new visit.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 30, 60, 120).forEach { s ->
                                Chip(label = if (s < 60) "${s}s" else "${s / 60} min", selected = Store.graceSec == s) {
                                    Store.chooseGrace(s)
                                }
                            }
                        }
                    }
                }

                item { SectionTitle("Keep it running") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Some phones stop background apps to save battery. If check-ins ever stop appearing, " +
                                "set Mind Your Apps to \"Unrestricted\" battery use.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${ctx.packageName}")
                                )
                            )
                        }) { Text("Open app info") }
                    }
                }
            }

            item {
                Text(
                    "Mind Your Apps has no internet permission. Everything stays on your phone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(t: PauseTheme) {
    val selected = Store.theme == t
    val pal = t.palette(isSystemInDarkTheme())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
            .clickable { Store.chooseTheme(t) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mini preview: background, a headline bar, then the primary and secondary buttons.
        Box(
            Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(10.dp))
                .background(if (t.blur) Color(0xFF55607A) else Color(pal.bg))
        ) {
            if (t.blur) Box(Modifier.fillMaxSize().background(Color(0x66101018)))
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(34.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(pal.text)))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(pal.primary)))
                Box(Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(pal.secondary)))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(t.label, Modifier.weight(1f), fontSize = 16.sp)
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------- overview

private val AppColors = listOf(
    Color(0xFF4C8DF6), Color(0xFFF2994A), Color(0xFF27AE8F), Color(0xFFE0568A),
    Color(0xFF9B6BE8), Color(0xFFE5B83A), Color(0xFF5BBF5B), Color(0xFFD9604C),
)

/** Every watched app keeps one colour, so it reads the same in the chart and in the list. */
private fun appColor(pkg: String): Color {
    val i = Store.apps.indexOf(pkg).coerceAtLeast(0)
    return AppColors[i % AppColors.size]
}

private fun shortTotal(v: Float, opens: Boolean): String {
    if (opens) return if (v < 1f) "–" else v.toInt().toString()
    val m = v.toInt()
    return when {
        m < 1 -> "–"
        m < 60 -> "${m}m"
        else -> "${m / 60}h${(m % 60).toString().padStart(2, '0')}"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewCard(tick: Int) {
    var showOpens by remember { mutableStateOf(false) }
    val apps = Store.apps
    val week = remember(apps, tick) {
        // week[app][day]; today gets the minutes of a session that is still running
        apps.map { pkg ->
            val days = Store.week(pkg)
            val live = MindService.instance?.liveExtraMs(pkg) ?: 0L
            days.mapIndexed { i, d -> if (i == days.lastIndex) d.copy(ms = d.ms + live) else d }
        }
    }
    val days = week.firstOrNull()?.map { it.day } ?: emptyList()
    val values = week.map { perApp -> perApp.map { if (showOpens) it.opens.toFloat() else it.ms / 60_000f } }
    val totals = days.indices.map { d -> values.sumOf { it[d].toDouble() }.toFloat() }
    val maxTotal = (totals.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val grand = totals.sum()
    val today = LocalDate.now().toEpochDay()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyBar = MaterialTheme.colorScheme.outlineVariant

    val todayStats = week.mapNotNull { it.lastOrNull() }
    val opensToday = todayStats.sumOf { it.opens }
    val msToday = todayStats.sumOf { it.ms }
    val headline = if (opensToday == 0 && msToday < 60_000L) {
        "Nothing opened yet today"
    } else {
        "$opensToday ${if (opensToday == 1) "open" else "opens"} · ${fmtMinutes(msToday)}"
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Today", fontSize = 13.sp, color = label)
                Text(headline, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Last 7 days", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (showOpens) {
                            "${grand.toInt()} opens · ${"%.1f".format(grand / 7f)} per day"
                        } else {
                            fmtMinutes((grand * 60_000f).toLong()) + " · " +
                                fmtMinutes((grand / 7f * 60_000f).toLong()) + " per day"
                        },
                        fontSize = 13.sp,
                        color = label,
                    )
                }
                Chip("Time", !showOpens) { showOpens = false }
                Spacer(Modifier.width(6.dp))
                Chip("Opens", showOpens) { showOpens = true }
            }

            Box(Modifier.fillMaxWidth().height(150.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val n = days.size.coerceAtLeast(1)
                    val col = size.width / n
                    val barW = col * 0.58f
                    val gap = 1.5.dp.toPx()
                    val radius = CornerRadius(8.dp.toPx())
                    for (d in days.indices) {
                        val x = d * col + (col - barW) / 2f
                        if (totals[d] <= 0f) {
                            drawRoundRect(
                                color = emptyBar,
                                topLeft = Offset(x, size.height - 4.dp.toPx()),
                                size = Size(barW, 4.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            continue
                        }
                        val top = values.indices.lastOrNull { values[it][d] > 0f } ?: continue
                        var y = size.height
                        for (a in values.indices) {
                            val v = values[a][d]
                            if (v <= 0f) continue
                            val h = (v / maxTotal) * size.height
                            val segTop = y - h
                            val color = AppColors[a % AppColors.size]
                            if (a == top) {
                                // rounded cap on the top segment, square where it meets the one below
                                drawRoundRect(color, Offset(x, segTop), Size(barW, h), radius)
                                if (h > radius.y) {
                                    drawRect(color, Offset(x, segTop + h / 2f), Size(barW, h / 2f - gap / 2f))
                                }
                            } else {
                                drawRect(color, Offset(x, segTop + gap / 2f), Size(barW, (h - gap).coerceAtLeast(1f)))
                            }
                            y = segTop
                        }
                    }
                }
                if (grand <= 0f) {
                    Text(
                        "Your week fills in as you go",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 14.sp,
                        color = label,
                    )
                }
            }

            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, day ->
                    val isToday = day == today
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            LocalDate.ofEpochDay(day).dayOfWeek
                                .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else label,
                        )
                        Text(shortTotal(totals[i], showOpens), fontSize = 11.sp, color = label, maxLines = 1)
                    }
                }
            }

            Text(
                "Opens count when you continue into the app.",
                fontSize = 12.sp,
                color = label,
            )
        }
    }
}

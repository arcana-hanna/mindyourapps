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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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

    /** Bumped on every resume so the setup card re-checks the accessibility switch. */
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
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
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
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.Detail) Screen.Home else Screen.Home
    }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            resumeTick,
            onAdd = { screen = Screen.Picker },
            onOpen = { screen = Screen.Detail(it) },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Picker -> PickerScreen(onDone = { screen = Screen.Home })
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!serviceOn) item { SetupCard() }

            if (Store.apps.isEmpty()) {
                item { EmptyState(onAdd) }
            } else {
                items(Store.apps, key = { it }) { pkg ->
                    AppRow(pkg, tick, onOpen)
                }
            }
        }
    }
}

@Composable
private fun SetupCard() {
    val ctx = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("One quick setup step", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Turn on the accessibility service for Mind Your Apps. It only notices which app " +
                    "opens, it never reads your screen, and nothing leaves your phone.",
                fontSize = 14.sp,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    "Android 13 and newer: if the switch is greyed out, tap \"App info\", then the " +
                        "three dots at the top right, then \"Allow restricted settings\". " +
                        "Come back and try again.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Button(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Open accessibility settings") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }) { Text("App info (allow restricted settings)") }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
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

@Composable
private fun AppRow(pkg: String, tick: Int, onOpen: (String) -> Unit) {
    val live = MindService.instance?.liveExtraMs(pkg) ?: 0L
    val stat = remember(pkg, tick) { Store.stats(pkg, Store.today()) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable { onOpen(pkg) },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(pkg)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(appLabel(pkg), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
}

// ---------------------------------------------------------------------- picker

@Composable
private fun PickerScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = loadApps(ctx.packageManager, ctx.packageName).also { list -> list.forEach { AppCache.put(it) } }
    }
    var query by remember { mutableStateOf("") }
    BackHandler(onBack = onDone)

    Scaffold(topBar = { Bar("Choose apps", onDone) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading your apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val shown = list.filter { it.label.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.pkg }) { a ->
                        val on = a.pkg in Store.apps
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (on) Store.removeApp(a.pkg) else Store.addApp(a.pkg) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (a.icon != null) {
                                Image(
                                    BitmapPainter(a.icon), null,
                                    Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
                                )
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
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- detail

@Composable
private fun DetailScreen(pkg: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val week = remember(pkg) { Store.week(pkg) }
    var msg by remember(pkg) { mutableStateOf(Store.message(pkg)) }
    var confirmRemove by remember { mutableStateOf(false) }
    val label = appLabel(pkg)

    Scaffold(topBar = { Bar(label, onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("Pause before opening", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = Store.isEnabled(pkg), onCheckedChange = { Store.setEnabled(pkg, it) })
                }
            }
            item {
                ChartCard("Opens, last 7 days", week.map { it.day to it.opens.toFloat() }, week.sumOf { it.opens }.toString() + " total")
            }
            item {
                ChartCard("Minutes, last 7 days", week.map { it.day to (it.ms / 60_000f) }, fmtMinutes(week.sumOf { it.ms }) + " total")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your own message", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = msg,
                        onValueChange = { msg = it; Store.saveMessage(pkg, it) },
                        placeholder = { Text("Leave empty for the friendly random lines") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
private fun ChartCard(title: String, data: List<Pair<Long, Float>>, summary: String) {
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
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
                    val d = LocalDate.ofEpochDay(day).dayOfWeek.name.take(1)
                    Text(d, fontSize = 12.sp, color = label, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var newLine by remember { mutableStateOf("") }
    var previewNote by remember { mutableStateOf(false) }

    Scaffold(topBar = { Bar("Settings", onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { SectionTitle("Look of the pause screen") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PauseTheme.entries.forEach { t -> ThemeRow(t) }
                    Button(onClick = {
                        val s = MindService.instance
                        if (s != null) {
                            // Show it only after we've left the screen, so it covers a real app.
                            s.showPreview()
                            previewNote = false
                        } else previewNote = true
                    }) { Text("Preview") }
                    if (previewNote) {
                        Text(
                            "Turn on the accessibility service first (see the card on the home screen).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

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

            item { SectionTitle("Friendly lines") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show a random friendly line", Modifier.weight(1f))
                    Switch(checked = Store.friendly, onCheckedChange = { Store.useFriendly(it) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newLine,
                        onValueChange = { newLine = it },
                        placeholder = { Text("Add your own line") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { Store.addLine(newLine); newLine = "" }, enabled = newLine.isNotBlank()) {
                        Text("Add")
                    }
                }
            }
            items(Store.customLines, key = { "c$it" }) { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line, Modifier.weight(1f).padding(vertical = 10.dp), fontSize = 14.sp)
                    IconButton(onClick = { Store.removeLine(line) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove")
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
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = fg, fontSize = 14.sp) }
}

@Composable
private fun ThemeRow(t: PauseTheme) {
    val selected = Store.theme == t
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
                .background(
                    if (t.blur) Color(0xFF55607A) else Color(t.bg)
                )
        ) {
            if (t.blur) Box(Modifier.fillMaxSize().background(Color(0x66101018)))
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(34.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.text)))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(t.primary)))
                Box(Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(t.secondary)))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(t.label, Modifier.weight(1f), fontSize = 16.sp)
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

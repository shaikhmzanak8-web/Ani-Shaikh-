package com.mzanak.animedl

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

val ACCENTS = listOf(
    Color(0xFF9E9E9E), Color(0xFF64B5F6), Color(0xFFB388FF),
    Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFE57373)
)

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var accent by mutableIntStateOf(p.getInt("accent", 0)); private set
    var amoled by mutableStateOf(p.getBoolean("amoled", false)); private set
    var quality by mutableIntStateOf(p.getInt("quality", 1)); private set
    var wifiOnly by mutableStateOf(p.getBoolean("wifi", false)); private set
    var dub by mutableStateOf(p.getBoolean("dub", false)); private set
    fun setAccent(v: Int) { accent = v; p.edit().putInt("accent", v).apply() }
    fun setAmoled(v: Boolean) { amoled = v; p.edit().putBoolean("amoled", v).apply() }
    fun setQuality(v: Int) { quality = v; p.edit().putInt("quality", v).apply() }
    fun setWifiOnly(v: Boolean) { wifiOnly = v; p.edit().putBoolean("wifi", v).apply() }
    fun setDub(v: Boolean) { dub = v; p.edit().putBoolean("dub", v).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent { Root(prefs) }
    }
}

@Composable
fun Root(st: Prefs) {
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= 28) need += Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) perm.launch(need.toTypedArray())
    }
    val bg = if (st.amoled) Color.Black else Color(0xFF121212)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ACCENTS[st.accent], onPrimary = Color.Black,
            background = bg, surface = Color(0xFF1E1E1E),
            onBackground = Color(0xFFE0E0E0), onSurface = Color(0xFFE0E0E0),
            secondaryContainer = Color(0xFF2C2C2C), onSecondaryContainer = Color(0xFFE0E0E0),
            surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFBDBDBD)
        )
    ) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var sel by remember { mutableStateOf<Anime?>(null) }
        BackHandler(sel != null) { sel = null }
        val names = listOf("Home", "Search", "Settings")
        val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Settings)
        Scaffold(
            containerColor = bg,
            bottomBar = {
                Column(Modifier.background(Color(0xFF1A1A1A))) {
                    Text(
                        "Developed by Shaikh Mzanak",
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.Gray
                    )
                    NavigationBar(containerColor = Color(0xFF1A1A1A)) {
                        names.forEachIndexed { i, n ->
                            NavigationBarItem(
                                selected = tab == i && sel == null,
                                onClick = { tab = i; sel = null },
                                icon = { Icon(icons[i], n) },
                                label = { Text(n) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                val cur = sel
                if (cur != null) Detail(cur, st) { sel = null }
                else when (tab) {
                    0 -> Home { sel = it }
                    1 -> SearchTab(st) { sel = it }
                    else -> SettingsTab(st)
                }
            }
        }
    }
}

@Composable
fun AnimeRow(a: Anime, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)) {
        AsyncImage(
            model = a.cover, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(60.dp, 86.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.align(Alignment.CenterVertically)) {
            Text(a.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val sub = listOfNotNull(a.status.ifBlank { null }, a.episodes?.let { "$it eps" }).joinToString(" - ")
            Text(sub, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun Home(onPick: (Anime) -> Unit) {
    var list by remember { mutableStateOf<List<Anime>?>(null) }
    var err by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { list = Api.anilist(null) } catch (e: Exception) { err = true } }
    Column(Modifier.fillMaxSize()) {
        Text("Airing now", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        val l = list
        when {
            err -> Text("Couldn't load the list. Check your connection.", Modifier.padding(16.dp), color = Color.Gray)
            l == null -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn { items(l, key = { it.id }) { AnimeRow(it) { onPick(it) } } }
        }
    }
}

@Composable
fun SearchTab(st: Prefs, onPick: (Anime) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    fun go() {
        if (q.isBlank() || q.startsWith("http")) return
        scope.launch {
            loading = true
            res = try { Api.anilist(q.trim()) } catch (e: Exception) { emptyList() }
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q,
            onValueChange = { text: String -> q = text },
            singleLine = true,
            placeholder = { Text("Search anime or paste a direct link") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }),
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        )
        if (q.startsWith("http")) {
            Button(
                onClick = {
                    val u = q.trim()
                    Api.enqueue(ctx, DlFile("", u.substringBefore('?').substringAfterLast('/').ifBlank { "file" }, u, 0), st.wifiOnly)
                    Toast.makeText(ctx, "Download started", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(horizontal = 14.dp)
            ) { Text("Download this link") }
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(30.dp), Alignment.Center) { CircularProgressIndicator() }
        LazyColumn { items(res, key = { it.id }) { AnimeRow(it) { onPick(it) } } }
    }
}

@Composable
fun Detail(a: Anime, st: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf<List<DlFile>?>(null) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(a.id) { files = try { Api.archive(a.title) } catch (e: Exception) { emptyList() } }
    val shown = remember(files, st.dub, st.quality) { Api.filter(files ?: emptyList(), st.dub, st.quality) }
    val picked = shown.filter { it.url in checked }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(a.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !st.dub, onClick = { st.setDub(false) }, label = { Text("Sub") })
            FilterChip(selected = st.dub, onClick = { st.setDub(true) }, label = { Text("Dub") })
        }
        Spacer(Modifier.height(6.dp))
        when {
            files == null -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator() }
            shown.isEmpty() -> Text(
                "No free, legal downloads found for this title with these options. Try the other audio option, or paste a direct link in Search.",
                Modifier.padding(16.dp), color = Color.Gray
            )
            else -> {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        checked = if (picked.size == shown.size) emptySet() else shown.map { it.url }.toSet()
                    }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = picked.size == shown.size, onCheckedChange = {
                        checked = if (it) shown.map { f -> f.url }.toSet() else emptySet()
                    })
                    Text("Select all (${shown.size})")
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.url }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                checked = if (f.url in checked) checked - f.url else checked + f.url
                            }.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = f.url in checked, onCheckedChange = {
                                checked = if (it) checked + f.url else checked - f.url
                            })
                            Column {
                                Text(f.name.substringAfterLast('/'), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(Api.fmt(f.size), color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Button(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        picked.forEach { Api.enqueue(ctx, it, st.wifiOnly) }
                        Toast.makeText(ctx, "Started ${picked.size} download(s)", Toast.LENGTH_SHORT).show()
                        checked = emptySet()
                    },
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                ) { Text("Download ${picked.size} selected") }
            }
        }
    }
}

@Composable
fun SettingsTab(st: Prefs) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text("Accent color", color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ACCENTS.forEachIndexed { i, c ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c)
                        .border(if (i == st.accent) 2.dp else 0.dp, Color.White, CircleShape)
                        .clickable { st.setAccent(i) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pure black background", Modifier.weight(1f))
            Switch(checked = st.amoled, onCheckedChange = { st.setAmoled(it) })
        }
        Text("Download quality (file size limit)", color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Data saver", "Standard", "High").forEachIndexed { i, n ->
                FilterChip(selected = st.quality == i, onClick = { st.setQuality(i) }, label = { Text(n) })
            }
        }
        Text("Default audio", color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !st.dub, onClick = { st.setDub(false) }, label = { Text("Sub") })
            FilterChip(selected = st.dub, onClick = { st.setDub(true) }, label = { Text("Dub") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Download on Wi-Fi only", Modifier.weight(1f))
            Switch(checked = st.wifiOnly, onCheckedChange = { st.setWifiOnly(it) })
        }
        Text("Files are saved to Downloads/Anime on your phone.", color = Color.Gray, fontSize = 12.sp)
    }
}

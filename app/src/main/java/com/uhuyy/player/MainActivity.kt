package com.uhuyy.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppTheme(
    val name: String, val bg: Color, val panel: Color, val acc: Color,
    val mute: Color, val txt: Color, val light: Boolean = false
)

internal object ThemeState {
    val all = listOf(
        AppTheme("Gelap Biru", Color(0xFF0C0F14), Color(0xFF161B23), Color(0xFF4FB0FF), Color(0xFF8A94A4), Color(0xFFE9EDF2)),
        AppTheme("AMOLED Hitam", Color(0xFF000000), Color(0xFF0F0F0F), Color(0xFF00E676), Color(0xFF8C8C8C), Color(0xFFFFFFFF)),
        AppTheme("Terang", Color(0xFFF6F7F9), Color(0xFFFFFFFF), Color(0xFF1E88E5), Color(0xFF6B7280), Color(0xFF111827), true),
        AppTheme("Ungu Neon", Color(0xFF100B1F), Color(0xFF1A1330), Color(0xFFB388FF), Color(0xFF9E92B8), Color(0xFFF1EAFF)),
        AppTheme("Merah Marun", Color(0xFF140A0C), Color(0xFF221114), Color(0xFFFF5A6E), Color(0xFFB09095), Color(0xFFFBEAEC)),
        AppTheme("Hijau Hutan", Color(0xFF08120D), Color(0xFF10201A), Color(0xFF4ADE80), Color(0xFF8DA89A), Color(0xFFE6F4EC)),
        AppTheme("Oranye Senja", Color(0xFF15100A), Color(0xFF221A10), Color(0xFFFFA13D), Color(0xFFB3A28C), Color(0xFFFFF1E0))
    )
    var idx by mutableIntStateOf(0)
    val cur: AppTheme get() = all[idx]
    fun load(ctx: Context) {
        idx = ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).getInt("theme", 0).coerceIn(0, all.lastIndex)
    }
    fun set(ctx: Context, i: Int) {
        idx = i
        ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).edit().putInt("theme", i).apply()
    }
}

private val Bg: Color get() = ThemeState.cur.bg
private val Panel: Color get() = ThemeState.cur.panel
private val Acc: Color get() = ThemeState.cur.acc
private val Mute: Color get() = ThemeState.cur.mute
private val Txt: Color get() = ThemeState.cur.txt
private const val ALL = "\u0000all"

data class Vid(
    val id: Long,
    val name: String,
    val dur: Long,
    val size: Long,
    val bucket: String,
    val added: Long
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
}

class Lib(val vids: List<Vid>, val pos: Map<Long, Long>)

internal fun fmtTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}

private fun loadLib(ctx: Context): Lib {
    val list = ArrayList<Vid>()
    val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED)
    ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val iAdded = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        while (c.moveToNext()) {
            list.add(
                Vid(
                    c.getLong(iId),
                    c.getString(iName) ?: "Video",
                    c.getLong(iDur),
                    c.getLong(iSize),
                    c.getString(iBucket) ?: "Lainnya",
                    c.getLong(iAdded)
                )
            )
        }
    }
    val prefs = ctx.getSharedPreferences("pos", Context.MODE_PRIVATE)
    return Lib(list, list.associate { it.id to prefs.getLong(it.uri.toString(), 0L) })
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(this)
        setContent {
            val t = ThemeState.cur
            val view = LocalView.current
            SideEffect {
                window.statusBarColor = t.bg.toArgb()
                window.navigationBarColor = t.bg.toArgb()
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = t.light
                c.isAppearanceLightNavigationBars = t.light
            }
            val scheme = if (t.light) lightColorScheme(
                primary = t.acc, background = t.bg, surface = t.panel,
                onBackground = t.txt, onSurface = t.txt
            ) else darkColorScheme(
                primary = t.acc, background = t.bg, surface = t.panel,
                onBackground = t.txt, onSurface = t.txt
            )
            MaterialTheme(colorScheme = scheme) { Library() }
        }
    }
}

@Composable
private fun Library() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perm) }
    var lib by remember { mutableStateOf<Lib?>(null) }
    LaunchedEffect(granted, refresh) {
        if (granted) lib = withContext(Dispatchers.IO) { loadLib(ctx) }
    }
    val loader = remember { ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build() }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        val l = lib
        when {
            !granted -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Aplikasi butuh izin baca video supaya bisa nampilin semua video di HP lu. Nggak ada yang dikirim ke internet.", color = Mute)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(perm) }) { Text("Izinkan") }
            }
            l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Memuat video...", color = Mute) }
            else -> Content(l, loader)
        }
    }
}

@Composable
private fun Content(lib: Lib, loader: ImageLoader) {
    val ctx = LocalContext.current
    var folder by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    BackHandler(enabled = folder != null) { folder = null; query = "" }

    if (folder == null) {
        val groups = remember(lib) {
            lib.vids.groupBy { it.bucket }.toList().sortedByDescending { g -> g.second.maxOf { it.added } }
        }
        Column(Modifier.fillMaxSize()) {
            var showTheme by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Video", fontSize = 22.sp, modifier = Modifier.weight(1f))
                Text(
                    "Tema", color = Acc, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Panel)
                        .clickable { showTheme = true }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            if (showTheme) {
                AlertDialog(
                    onDismissRequest = { showTheme = false },
                    containerColor = Panel,
                    title = { Text("Pilih tema", color = Txt) },
                    text = {
                        Column {
                            ThemeState.all.forEachIndexed { i, th ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { ThemeState.set(ctx, i); showTheme = false }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(22.dp).clip(CircleShape).background(th.acc))
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        th.name, color = Txt, modifier = Modifier.weight(1f)
                                    )
                                    if (i == ThemeState.idx) Text("\u2713", color = Acc)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Tutup", color = Acc) } }
                )
            }
            if (lib.vids.isEmpty()) {
                Text("Belum ada video di HP ini.", color = Mute, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (lib.vids.isNotEmpty()) {
                    item {
                        FolderRow("Semua video", lib.vids.size, lib.vids.first().uri, loader) { folder = ALL }
                    }
                }
                itemsIndexed(groups, key = { _, g -> g.first }) { _, g ->
                    FolderRow(g.first, g.second.size, g.second.first().uri, loader) { folder = g.first }
                }
            }
        }
    } else {
        val base = if (folder == ALL) lib.vids else lib.vids.filter { it.bucket == folder }
        val shown = remember(base, query, sort) {
            val f = base.filter { it.name.contains(query, ignoreCase = true) }
            when (sort) {
                0 -> f.sortedByDescending { it.added }
                1 -> f.sortedBy { it.name.lowercase() }
                else -> f.sortedByDescending { it.size }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", fontSize = 24.sp, modifier = Modifier.clickable { folder = null; query = "" }.padding(end = 14.dp))
                Text(
                    if (folder == ALL) "Semua video" else folder ?: "", fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(
                    listOf("Terbaru", "Nama", "Ukuran")[sort], color = Acc, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Panel)
                        .clickable { sort = (sort + 1) % 3 }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Cari video") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, v -> v.id }) { i, v ->
                    val p = lib.pos[v.id] ?: 0L
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            ctx.startActivity(
                                Intent(ctx, PlayerActivity::class.java)
                                    .putExtra("ids", shown.map { it.id }.toLongArray())
                                    .putExtra("index", i)
                            )
                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Thumb(v.uri, loader, Modifier.size(124.dp, 70.dp))
                            Text(
                                fmtTime(v.dur), fontSize = 11.sp, color = Color.White,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(fmtSize(v.size), color = Mute, fontSize = 12.sp)
                            if (p > 5000 && v.dur > 0) {
                                LinearProgressIndicator(
                                    progress = { (p.toFloat() / v.dur).coerceIn(0f, 1f) },
                                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, count: Int, thumb: Uri, loader: ImageLoader, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(thumb, loader, Modifier.size(64.dp, 48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count video", color = Mute, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Thumb(uri: Uri, loader: ImageLoader, mod: Modifier) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(uri).videoFrameMillis(1000).build(),
        imageLoader = loader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = mod.clip(RoundedCornerShape(8.dp)).background(Panel)
    )
}

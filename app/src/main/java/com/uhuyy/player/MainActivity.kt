package com.uhuyy.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.util.Size
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream

// Warna tetap (tema warna sudah dibuang). Latar bisa diganti foto dari galeri.
internal val Accent = Color(0xFF4FB0FF)
private val BaseBg = Color(0xFF0C0F14)
private val Panel = Color(0xAA161B23)
private val DialogBg = Color(0xFF161B23)
private val Mute = Color(0xFFB4BCC8)
private val Txt = Color.White
private const val ALL = "\u0000all"

// Simpan foto latar: dikecilkan max ~2400px lalu disimpan di folder app,
// jadi tetap ada walau foto aslinya dihapus dari galeri.
internal object ThemeState {
    var bright by mutableFloatStateOf(1f)
    var bg by mutableStateOf<ImageBitmap?>(null)

    private fun file(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    fun loadPrefs(ctx: Context) {
        bright = ctx.getSharedPreferences("pos", Context.MODE_PRIVATE)
            .getFloat("bright", 1f).coerceIn(0.2f, 1.6f)
    }

    fun saveBright(ctx: Context) {
        ctx.getSharedPreferences("pos", Context.MODE_PRIVATE).edit().putFloat("bright", bright).apply()
    }

    private fun readFile(ctx: Context): ImageBitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    suspend fun loadImage(ctx: Context) {
        val b = withContext(Dispatchers.IO) { readFile(ctx) }
        bg = b
    }

    // Baca foto dari galeri jadi Bitmap (maks ~2400 px) buat layar atur posisi.
    suspend fun decodeUri(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(ctx.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    val m = maxOf(info.size.width, info.size.height)
                    var s = 1
                    while (m / s > 2400) s *= 2
                    dec.setTargetSampleSize(s)
                    dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                }
            } else {
                legacyDecode(ctx, uri)
            }
        }.getOrNull()
    }

    // Simpan potongan foto (sx, sy, sw, sh = area di foto asli) yang dipilih di layar atur posisi.
    // Hasil disimpan seukuran area Home (maks 1440 px lebar), jadi persis yang terlihat saat diatur.
    suspend fun saveCrop(ctx: Context, src: Bitmap, sx: Float, sy: Float, sw: Float, sh: Float): Boolean {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val l = sx.roundToInt().coerceIn(0, src.width - 1)
                val tp = sy.roundToInt().coerceIn(0, src.height - 1)
                val r = (sx + sw).roundToInt().coerceIn(l + 1, src.width)
                val b = (sy + sh).roundToInt().coerceIn(tp + 1, src.height)
                val outW = (r - l).coerceIn(1, 1440)
                val outH = (outW.toFloat() * (b - tp) / (r - l)).roundToInt().coerceAtLeast(1)
                val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(out)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                cv.drawBitmap(src, android.graphics.Rect(l, tp, r, b), android.graphics.Rect(0, 0, outW, outH), paint)
                FileOutputStream(file(ctx)).use { o -> out.compress(Bitmap.CompressFormat.JPEG, 92, o) }
                out.recycle()
                true
            }.getOrDefault(false)
        }
        if (ok) {
            val b = withContext(Dispatchers.IO) { readFile(ctx) }
            bg = b
        }
        return ok
    }

    // Android 5-8 (belum ada ImageDecoder)
    private fun legacyDecode(ctx: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        val m = maxOf(bounds.outWidth, bounds.outHeight)
        while (m / s > 2400) s *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = s }
        return ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts)!! }
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        bg = null
    }
}

// Thumbnail: pakai thumbnail bawaan sistem (di-cache sistem, jauh lebih ringan
// daripada decode frame video satu-satu). Cache memori 24 MB, maksimal 3 proses sekaligus.
internal object Thumbs {
    private val cache = object : LruCache<Long, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val gate = Semaphore(3)

    fun cached(id: Long): ImageBitmap? = cache.get(id)

    suspend fun load(ctx: Context, id: Long): ImageBitmap? {
        cache.get(id)?.let { return it }
        val bmp = gate.withPermit { withContext(Dispatchers.IO) { fetch(ctx, id) } } ?: return null
        val ib = bmp.asImageBitmap()
        cache.put(id, ib)
        return ib
    }

    private fun fetch(ctx: Context, id: Long): Bitmap? {
        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        if (Build.VERSION.SDK_INT >= 29) {
            val b = runCatching { ctx.contentResolver.loadThumbnail(uri, Size(360, 202), null) }.getOrNull()
            if (b != null) return b
        } else {
            @Suppress("DEPRECATION")
            val b = runCatching {
                MediaStore.Video.Thumbnails.getThumbnail(ctx.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            }.getOrNull()
            if (b != null) return b
        }
        return frame(ctx, uri)
    }

    // Cadangan kalau thumbnail sistem nggak ada: ambil satu frame lalu kecilin.
    private fun frame(ctx: Context, uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val raw = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0L)
            raw?.let { b ->
                val sc = 360f / maxOf(b.width, b.height)
                if (sc < 1f) Bitmap.createScaledBitmap(
                    b, (b.width * sc).toInt().coerceAtLeast(1), (b.height * sc).toInt().coerceAtLeast(1), true
                ) else b
            }
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

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
    val tt = (ms / 1000).coerceAtLeast(0)
    val h = tt / 3600
    val m = (tt % 3600) / 60
    val s = tt % 60
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
        ThemeState.loadPrefs(this)
        Lang.load(this)
        setContent {
            val view = LocalView.current
            SideEffect {
                window.statusBarColor = BaseBg.toArgb()
                window.navigationBarColor = BaseBg.toArgb()
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = false
                c.isAppearanceLightNavigationBars = false
            }
            val scheme = darkColorScheme(
                primary = Accent, background = BaseBg, surface = DialogBg,
                onBackground = Txt, onSurface = Txt
            )
            val appCtx = LocalContext.current.applicationContext
            LaunchedEffect(Unit) { ThemeState.loadImage(appCtx) }
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
    var page by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(granted, refresh) {
        if (granted) lib = withContext(Dispatchers.IO) { loadLib(ctx) }
    }

    Surface(color = BaseBg, contentColor = Txt, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            val img = ThemeState.bg
            if (img != null) {
                val b = ThemeState.bright
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                    colorFilter = ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                b, 0f, 0f, 0f, 0f,
                                0f, b, 0f, 0f, 0f,
                                0f, 0f, b, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                )
                Box(Modifier.fillMaxSize().background(Color(0x33000000)))
            }
            val l = lib
            Box(Modifier.fillMaxSize().alpha(if (page == 0) 1f else 0f)) {
            when {
                !granted -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(t("perm_text"), color = Mute)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(perm) }) { Text(t("allow")) }
                }
                l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t("loading"), color = Mute) }
                else -> Content(l) { page = 1 }
            }
            }
            if (page != 0) SettingsHost(page, pending, { pending = it }, { page = it })
        }
    }
}

@Composable
internal fun Pill(label: String, click: () -> Unit) {
    Text(
        label, color = Accent, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF))
            .clickable(onClick = click).padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun Content(lib: Lib, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    BackHandler(enabled = folder != null) { folder = null; query = "" }

    if (folder == null) {
        val groups = remember(lib) {
            lib.vids.groupBy { it.bucket }.toList().sortedByDescending { g -> g.second.maxOf { it.added } }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Uhuyy Player", fontSize = 22.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Panel).clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Icon24(Ic.GEAR, Accent, 22.dp) }
            }
            if (lib.vids.isEmpty()) {
                Text(t("no_videos"), color = Mute, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (lib.vids.isNotEmpty()) {
                    item {
                        FolderRow(t("all_videos"), lib.vids.size, lib.vids.first().id) { folder = ALL }
                    }
                }
                itemsIndexed(groups, key = { _, g -> g.first }) { _, g ->
                    FolderRow(g.first, g.second.size, g.second.first().id) { folder = g.first }
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
                    if (folder == ALL) t("all_videos") else folder ?: "", fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text(
                    listOf(t("sort_new"), t("sort_name"), t("sort_size"))[sort], color = Accent, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Panel)
                        .clickable { sort = (sort + 1) % 3 }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(t("search")) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    .background(Color(0x66000000), RoundedCornerShape(12.dp))
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
                            Thumb(v.id, Modifier.size(124.dp, 70.dp))
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
private fun FolderRow(name: String, count: Int, thumbId: Long, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(thumbId, Modifier.size(64.dp, 48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tf("n_videos", count), color = Mute, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Thumb(id: Long, mod: Modifier) {
    val ctx = LocalContext.current.applicationContext
    val img by produceState<ImageBitmap?>(Thumbs.cached(id), id) {
        if (value == null) {
            delay(70) // item yang cuma lewat pas di-fling nggak ikut diproses
            value = Thumbs.load(ctx, id)
        }
    }
    Box(mod.clip(RoundedCornerShape(8.dp)).background(Panel)) {
        img?.let {
            Image(
                bitmap = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

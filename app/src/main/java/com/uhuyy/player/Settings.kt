package com.uhuyy.player

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

// Tanggal pembuatan app (tampil di Tentang). Ganti string ini kalau mau ubah.
private const val BUILD_DATE = "2026-10-27"
private const val CREATOR = "Choing"

private val SPanel = Color(0xCC161B23)
private val SMute = Color(0xFFB4BCC8)
private val SIconBg = Color(0x334FB0FF)

// Nomor halaman: 0 = Home, 1 = Pengaturan, 2 = Tema, 3 = Bahasa, 4 = Tentang, 5 = Atur foto
@Composable
internal fun SettingsHost(
    page: Int,
    pending: Bitmap?,
    setPending: (Bitmap?) -> Unit,
    go: (Int) -> Unit
) {
    if (page == 5 && pending != null) {
        WallpaperAdjust(
            bmp = pending,
            onDone = { setPending(null); go(2) },
            onCancel = { setPending(null); go(2) }
        )
        return
    }
    // Penahan sentuhan supaya Home di bawahnya nggak ikut kepencet
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        when (page) {
            1 -> SettingsPage(go)
            2 -> ThemePage(go) { setPending(it); go(5) }
            3 -> LanguagePage(go)
            4 -> AboutPage(go)
            else -> {}
        }
    }
}

@Composable
private fun PageBox(title: String, onBack: () -> Unit, body: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon24(Ic.BACK, Color.White, 24.dp) }
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 20.sp, color = Color.White)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            body()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NavRow(ic: Ic, title: String, sub: String?, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SPanel)
            .clickable(onClick = click).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SIconBg),
            contentAlignment = Alignment.Center
        ) { Icon24(ic, Accent, 22.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = Color.White)
            if (sub != null) Text(sub, fontSize = 13.sp, color = SMute)
        }
        Icon24(Ic.CHEV, SMute, 18.dp)
    }
}

@Composable
private fun SettingsPage(go: (Int) -> Unit) {
    PageBox(t("settings"), { go(0) }) {
        NavRow(Ic.IMG, t("theme"), null) { go(2) }
        Spacer(Modifier.height(10.dp))
        val cur = if (Lang.pref == "sys") t("follow_phone") else Lang.names[Lang.codes.indexOf(Lang.pref)]
        NavRow(Ic.GLOBE, t("language"), cur) { go(3) }
        Spacer(Modifier.height(10.dp))
        NavRow(Ic.INFO, t("about"), null) { go(4) }
    }
}

@Composable
private fun ThemePage(go: (Int) -> Unit, onPicked: (Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val b = ThemeState.decodeUri(ctx, uri)
            if (b != null) onPicked(b)
            else Toast.makeText(ctx, t("photo_fail"), Toast.LENGTH_SHORT).show()
        }
    }
    PageBox(t("theme"), { go(1) }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SPanel).padding(16.dp)) {
            Text(t("bg_photo"), color = SMute, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                Pill(t("pick_photo")) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (ThemeState.bg != null) {
                    Spacer(Modifier.width(8.dp))
                    Pill(t("remove")) { ThemeState.clear(ctx) }
                }
            }
            if (ThemeState.bg != null) {
                Spacer(Modifier.height(18.dp))
                Text(t("bright_label"), color = SMute, fontSize = 13.sp)
                Slider(
                    value = ThemeState.bright,
                    onValueChange = { ThemeState.bright = it },
                    onValueChangeFinished = { ThemeState.saveBright(ctx) },
                    valueRange = 0.2f..1.6f
                )
            }
        }
    }
}

@Composable
private fun LanguagePage(go: (Int) -> Unit) {
    val ctx = LocalContext.current
    PageBox(t("language"), { go(1) }) {
        LangRow(t("follow_phone"), Lang.pref == "sys") { Lang.save(ctx, "sys") }
        Spacer(Modifier.height(10.dp))
        Lang.codes.forEachIndexed { i, c ->
            LangRow(Lang.names[i], Lang.pref == c) { Lang.save(ctx, c) }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(t("lang_note"), color = SMute, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun LangRow(name: String, selected: Boolean, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x554FB0FF) else SPanel)
            .clickable(onClick = click).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (selected) Icon24(Ic.CHECK, Accent, 22.dp)
    }
}

@Composable
private fun AboutPage(go: (Int) -> Unit) {
    val ctx = LocalContext.current
    val ver = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-"
    }
    val date = remember(Lang.code) {
        runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(BUILD_DATE)!!
            DateFormat.getDateInstance(DateFormat.LONG, Lang.locale).format(d)
        }.getOrDefault(BUILD_DATE)
    }
    PageBox(t("about"), { go(1) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF354044)),
                contentAlignment = Alignment.Center
            ) {
                // Logo asli app (foreground ikon). Diperbesar 1.44x supaya isinya penuh seperti di launcher.
                Image(
                    painterResource(R.drawable.ic_launcher_fg), null,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1.44f; scaleY = 1.44f },
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Uhuyy Player", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "v$ver", color = Accent, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(SIconBg)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(t("about_tag"), color = SMute, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SPanel).padding(horizontal = 16.dp, vertical = 6.dp)) {
            AboutLine(t("version"), ver)
            AboutLine(t("built_on"), date)
            AboutLine(t("creator"), CREATOR)
        }
        Spacer(Modifier.height(12.dp))
        InfoBlock(t("honest_title"), t("honest_text"))
        Spacer(Modifier.height(12.dp))
        InfoBlock(t("offline_title"), t("offline_text"))
    }
}

@Composable
private fun AboutLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = SMute, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun InfoBlock(title: String, text: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SPanel).padding(16.dp)) {
        Text(title, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

// Layar atur posisi foto latar. Area ini persis seukuran area Home, jadi yang tampak di sini
// = yang jadi latar. Geser untuk pindah, cubit untuk zoom (1x sampai 6x), lalu Terapkan.
@Composable
private fun WallpaperAdjust(bmp: Bitmap, onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val img = remember(bmp) { bmp.asImageBitmap() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }
    BackHandler { if (!busy) onCancel() }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { area = it }) {
        if (area.width > 0 && area.height > 0) {
            val w = area.width.toFloat()
            val h = area.height.toFloat()
            val s0 = maxOf(w / bmp.width, h / bmp.height)

            fun clampPan(p: Offset, z: Float): Offset {
                val mx = ((bmp.width * s0 * z - w) / 2f).coerceAtLeast(0f)
                val my = ((bmp.height * s0 * z - h) / 2f).coerceAtLeast(0f)
                return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
            }

            Canvas(
                Modifier.fillMaxSize().clipToBounds().pointerInput(area) {
                    detectTransformGestures { centroid, pc, zc, _ ->
                        val nz = (zoom * zc).coerceIn(1f, 6f)
                        val f = nz / zoom
                        val cc = centroid - Offset(w / 2f, h / 2f)
                        pan = clampPan(cc * (1f - f) + pan * f + pc, nz)
                        zoom = nz
                    }
                }
            ) {
                val dw = bmp.width * s0 * zoom
                val dh = bmp.height * s0 * zoom
                drawImage(
                    img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bmp.width, bmp.height),
                    dstOffset = IntOffset((w / 2f - dw / 2f + pan.x).roundToInt(), (h / 2f - dh / 2f + pan.y).roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
                )
                // Garis bantu sepertiga + bingkai
                val g = Color(0x55FFFFFF)
                drawLine(g, Offset(w / 3f, 0f), Offset(w / 3f, h), 1.dp.toPx())
                drawLine(g, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), 1.dp.toPx())
                drawLine(g, Offset(0f, h / 3f), Offset(w, h / 3f), 1.dp.toPx())
                drawLine(g, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), 1.dp.toPx())
                drawRect(Color(0xCCFFFFFF), Offset.Zero, Size(w, h), style = Stroke(3.dp.toPx()))
            }

            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(t("adjust_title"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(t("adjust_hint"), color = SMute, fontSize = 12.sp)
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    t("cancel"), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF))
                        .clickable(enabled = !busy) { onCancel() }.padding(vertical = 13.dp)
                )
                Text(
                    t("apply"), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Accent)
                        .clickable(enabled = !busy) {
                            busy = true
                            val s = s0 * zoom
                            val sx = bmp.width / 2f + (-w / 2f - pan.x) / s
                            val sy = bmp.height / 2f + (-h / 2f - pan.y) / s
                            scope.launch {
                                val ok = ThemeState.saveCrop(ctx, bmp, sx, sy, w / s, h / s)
                                busy = false
                                if (ok) onDone()
                                else Toast.makeText(ctx, t("photo_fail"), Toast.LENGTH_SHORT).show()
                            }
                        }.padding(vertical = 13.dp)
                )
            }
        }
    }
}

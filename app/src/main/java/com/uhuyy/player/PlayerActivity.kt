package com.uhuyy.player

import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Rational
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)
private val FIT_KEYS = arrayOf("fit_pas", "fit_crop", "fit_fill", "fit_stretch")
private val ORIENT_KEYS = arrayOf("rot_auto", "rot_land", "rot_port")
private val Scrim = Color(0x99000000)
private val BtnBg = Color(0x66000000)
private val InfoMute = Color(0xFFB4BCC8)
private val InfoBg = Color(0xFF161B23)

class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SharedPreferences
    private lateinit var audio: AudioManager
    private var locked by mutableStateOf(false)
    private var pinned = false
    private var inPip by mutableStateOf(false)
    private var vidTitle by mutableStateOf("")
    private var aspect by mutableFloatStateOf(16f / 9f)

    // Terang: -100..100. 0..100 = kecerahan layar sistem (0.0..1.0). Di bawah 0 = layar sistem
    // dipaksa 0.0 + lapisan hitam transparan makin pekat, jadi bisa lebih gelap dari batas sistem.
    private var brLevel = 50f
    private var dimAlpha by mutableFloatStateOf(0f)
    private var skipResume = false

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) addSubtitle(u)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pos", MODE_PRIVATE)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val items = buildItems()
        if (items.isEmpty()) {
            finish()
            return
        }
        // Sensor penuh: tetap bisa muter walau auto-rotate HP mati
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val cur = window.attributes.screenBrightness
        val b0 = if (cur in 0f..1f) cur else runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0f, 1f)
        brLevel = b0 * 100f

        player = ExoPlayer.Builder(this).build()
        player.setSeekParameters(SeekParameters.EXACT)
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                updatePip()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePip()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                vidTitle = nameOf(mediaItem.localConfiguration?.uri)
                if (skipResume) {
                    skipResume = false
                    return
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                val s = prefs.getLong(mediaItem.mediaId, 0L)
                if (s > 5000) player.seekTo(s)
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, t("cant_play"), Toast.LENGTH_SHORT).show()
            }
        })
        val start = intent.getIntExtra("index", 0).coerceIn(0, items.lastIndex)
        player.setMediaItems(items, start, C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) { PlayerScreen() }
        }
    }

    private fun item(u: Uri) = MediaItem.Builder().setUri(u).setMediaId(u.toString()).build()

    private fun buildItems(): List<MediaItem> {
        val ids = intent.getLongArrayExtra("ids")
        if (ids != null) {
            return ids.map { item(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it)) }
        }
        val d = intent.data ?: return emptyList()
        return listOf(item(d))
    }

    private fun nameOf(u: Uri?): String {
        if (u == null) return ""
        runCatching {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        }
        return u.lastPathSegment ?: ""
    }

    private fun savePos() {
        if (!::player.isInitialized) return
        val m = player.currentMediaItem ?: return
        val d = player.duration
        val p = player.currentPosition
        if (d > 0 && p > d - 5000) prefs.edit().remove(m.mediaId).apply()
        else if (p >= 5000) prefs.edit().putLong(m.mediaId, p).apply()
    }

    private fun addSubtitle(u: Uri) {
        val cur = player.currentMediaItem ?: return
        val mime = if (nameOf(u).lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
        val sub = MediaItem.SubtitleConfiguration.Builder(u)
            .setMimeType(mime)
            .setLanguage("id")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val p = player.currentPosition
        val i = player.currentMediaItemIndex
        skipResume = true
        player.replaceMediaItem(i, cur.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
        player.seekTo(i, p)
        Toast.makeText(this, t("sub_set"), Toast.LENGTH_SHORT).show()
    }

    // ---- Info video ----
    // Metadata file diambil dari MediaStore (video dari galeri/folder). Untuk video yang dibuka dari
    // app lain, Android sering cuma ngasih nama dan ukuran, jadi baris lain bisa kosong.
    private fun buildInfo(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val u = player.currentMediaItem?.localConfiguration?.uri
        out += t("i_name") to vidTitle

        var size = -1L
        var path = ""
        var folder = ""
        var dateMs = 0L
        var w0 = 0
        var h0 = 0
        if (u != null) {
            val rich = runCatching {
                val cols = ArrayList<String>()
                cols += MediaStore.MediaColumns.SIZE
                cols += MediaStore.MediaColumns.DATE_MODIFIED
                cols += MediaStore.Video.VideoColumns.DATE_TAKEN
                cols += MediaStore.MediaColumns.WIDTH
                cols += MediaStore.MediaColumns.HEIGHT
                cols += MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME
                if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.MediaColumns.RELATIVE_PATH
                else cols += MediaStore.MediaColumns.DATA
                contentResolver.query(u, cols.toTypedArray(), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        fun idx(n: String) = c.getColumnIndex(n)
                        idx(MediaStore.MediaColumns.SIZE).let { if (it >= 0) size = c.getLong(it) }
                        val taken = idx(MediaStore.Video.VideoColumns.DATE_TAKEN).let { if (it >= 0) c.getLong(it) else 0L }
                        val mod = idx(MediaStore.MediaColumns.DATE_MODIFIED).let { if (it >= 0) c.getLong(it) * 1000L else 0L }
                        dateMs = if (taken > 0) taken else mod
                        idx(MediaStore.MediaColumns.WIDTH).let { if (it >= 0) w0 = c.getInt(it) }
                        idx(MediaStore.MediaColumns.HEIGHT).let { if (it >= 0) h0 = c.getInt(it) }
                        idx(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME).let { if (it >= 0) folder = c.getString(it) ?: "" }
                        if (Build.VERSION.SDK_INT >= 29) {
                            idx(MediaStore.MediaColumns.RELATIVE_PATH).let { if (it >= 0) path = c.getString(it) ?: "" }
                        } else {
                            idx(MediaStore.MediaColumns.DATA).let {
                                if (it >= 0) path = (c.getString(it) ?: "").substringBeforeLast('/', "")
                            }
                        }
                    }
                }
            }.isSuccess
            if (!rich || size < 0) {
                runCatching {
                    contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) size = c.getLong(0)
                    }
                }
            }
        }
        val where = if (path.isNotEmpty()) path else folder
        if (where.isNotEmpty()) out += t("i_loc") to where
        if (size >= 0) out += t("sort_size") to infoSize(size)
        val d = player.duration
        if (d > 0) out += t("i_dur") to fmtTime(d)
        if (dateMs > 0) out += t("i_date") to SimpleDateFormat("d MMM yyyy, HH:mm", Lang.locale).format(Date(dateMs))

        val vf = player.videoFormat
        val af = player.audioFormat
        val vw = if (vf != null && vf.width > 0) vf.width else w0
        val vh = if (vf != null && vf.height > 0) vf.height else h0
        if (vw > 0 && vh > 0) out += t("i_res") to "$vw × $vh"
        if (vf?.sampleMimeType != null) out += t("i_vc") to codecName(vf.sampleMimeType)
        if (af?.sampleMimeType != null) {
            val extra = ArrayList<String>()
            if (af.channelCount > 0) extra += "${af.channelCount} ch"
            if (af.sampleRate > 0) extra += "${"%.1f".format(af.sampleRate / 1000f).removeSuffix(".0")} kHz"
            out += t("i_ac") to (codecName(af.sampleMimeType) + if (extra.isEmpty()) "" else " · " + extra.joinToString(" · "))
        }
        val fps = vf?.frameRate ?: -1f
        if (fps > 0f) {
            val r = (fps * 100).roundToInt() / 100f
            out += t("i_fps") to (if (r % 1f == 0f) "${r.toInt()} fps" else "$r fps")
        }
        var br = -1L
        if (vf != null && vf.bitrate > 0) br = vf.bitrate.toLong() + (if (af != null && af.bitrate > 0) af.bitrate.toLong() else 0L)
        else if (size > 0 && d > 0) br = size * 8000L / d
        if (br > 0) out += t("i_br") to (if (br >= 1_000_000L) "%.1f Mbps".format(br / 1_000_000.0) else "${br / 1000} kbps")
        return out
    }

    private fun infoSize(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
        b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
        else -> "%d KB".format(b / 1024)
    }

    private fun codecName(m: String?): String = when (m) {
        MimeTypes.VIDEO_H264 -> "H.264 (AVC)"
        MimeTypes.VIDEO_H265 -> "H.265 (HEVC)"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        MimeTypes.VIDEO_H263 -> "H.263"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "Dolby AC-3"
        MimeTypes.AUDIO_E_AC3 -> "Dolby E-AC-3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_DTS -> "DTS"
        else -> m ?: ""
    }

    // ---- Picture in Picture ----
    @RequiresApi(26)
    private fun pipParams(): PictureInPictureParams {
        val a = aspect.coerceIn(0.45f, 2.3f)
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational((a * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(player.isPlaying && !locked)
        return b.build()
    }

    private fun updatePip() {
        if (Build.VERSION.SDK_INT >= 26) runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && !locked) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && ::player.isInitialized && player.isPlaying && !locked) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    // ---- Kunci ----
    // full = false: kunci di dalam app (layar nggak nerima sentuhan, tombol back diblok, bar sistem disembunyiin).
    //               Nggak pakai screen pinning, jadi pas buka kunci nggak mental ke lock screen.
    // full = true : kunci penuh pakai sematan layar Android (Home/Recent ikut keblok),
    //               tapi pas dilepas Android bisa langsung nge-lock HP (tergantung setting HP).
    private fun lock(full: Boolean) {
        locked = true
        pinned = full
        if (full) runCatching { startLockTask() }
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        updatePip()
    }

    private fun unlock() {
        locked = false
        if (pinned) runCatching { stopLockTask() }
        pinned = false
        updatePip()
    }

    private fun applyBrightness() {
        val lp = window.attributes
        lp.screenBrightness = (brLevel.coerceAtLeast(0f) / 100f).coerceIn(0f, 1f)
        window.attributes = lp
        dimAlpha = if (brLevel < 0f) (-brLevel / 100f * 0.88f).coerceIn(0f, 0.88f) else 0f
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            savePos()
            player.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            savePos()
            player.release()
        }
    }

    // ---- UI ----
    @Composable
    private fun PlayerScreen() {
        val scope = rememberCoroutineScope()
        var showUi by remember { mutableStateOf(true) }
        var uiTick by remember { mutableIntStateOf(0) }
        var wantPlay by remember { mutableStateOf(true) }
        var pos by remember { mutableLongStateOf(0L) }
        var dur by remember { mutableLongStateOf(0L) }
        var speedI by remember { mutableIntStateOf(2) }
        var fitI by remember { mutableIntStateOf(0) }
        var orient by remember { mutableIntStateOf(0) }
        var loop by remember { mutableStateOf(false) }
        var hud by remember { mutableStateOf("") }
        var hudJob by remember { mutableStateOf<Job?>(null) }
        var stepping by remember { mutableStateOf(false) }
        var scrub by remember { mutableLongStateOf(0L) }
        var box by remember { mutableStateOf(IntSize.Zero) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var infoRows by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        val hold = remember {
            object {
                var tapJob: Job? = null
                var tapAt = 0L
                var seekAt = 0L
            }
        }

        fun say(msg: String) {
            hud = msg
            hudJob?.cancel()
            hudJob = scope.launch { delay(900); hud = "" }
        }

        fun toggle() {
            if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
        }

        // Seek 5 detik: sengaja tanpa notif biar layar tetap bersih
        fun seekBy(ms: Long) {
            val d = player.duration
            val tgt = (player.currentPosition + ms).coerceAtLeast(0L)
            player.seekTo(if (d > 0) tgt.coerceAtMost(d) else tgt)
        }

        fun fmtLevel(v: Float): String {
            val n = v.roundToInt()
            return if (n < 0) "−${-n}" else "$n"
        }

        // Lepas jari: di bawah 1x (atau nyaris 1x) balik normal pelan-pelan
        fun settleZoom() {
            if (zoom < 1.04f) {
                val z0 = zoom
                val p0 = pan
                scope.launch {
                    val a = Animatable(0f)
                    a.animateTo(1f, tween(380)) {
                        zoom = z0 + (1f - z0) * value
                        pan = p0 * (1f - value)
                    }
                }
            }
        }

        BackHandler(enabled = locked) { }

        LaunchedEffect(Unit) {
            var tick = 0
            while (true) {
                pos = player.currentPosition
                dur = player.duration.coerceAtLeast(0L)
                // wantPlay = niat pemutaran, bukan isPlaying. Buffering (misal habis seek) tetap dianggap main,
                // jadi kontrol nggak muncul lagi tiap habis double-tap seek.
                wantPlay = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                tick++
                if (tick % 12 == 0) savePos()
                delay(250)
            }
        }
        LaunchedEffect(showUi, wantPlay, uiTick, locked, stepping) {
            if (showUi && wantPlay && !locked && !stepping) {
                delay(3500)
                showUi = false
            }
        }
        LaunchedEffect(wantPlay) { if (!wantPlay && !locked) showUi = true }
        LaunchedEffect(inPip) {
            if (inPip) {
                zoom = 1f
                pan = Offset.Zero
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }) {
            AndroidView(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, null, false) as PlayerView).also {
                        it.player = player
                    }
                },
                update = { v ->
                    v.resizeMode = when (fitI) {
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    val k = if (fitI == 1) softScale(box.width, box.height, aspect) else 1f
                    v.scaleX = k
                    v.scaleY = k
                }
            )

            if (!inPip) {
                // 1. Lapisan gestur (satu pengendali: tap, double-tap, geser, pinch zoom)
                Box(
                    Modifier.fillMaxSize().pointerInput(locked) {
                        if (locked) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val slop = viewConfiguration.touchSlop
                            val t0 = System.currentTimeMillis()
                            val left = down.position.x < w / 2f
                            val zoomed0 = zoom > 1.01f
                            val startPos = player.currentPosition
                            val startLevel = brLevel
                            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                            var mode = 0 // 1 seek, 2 terang/volume, 3 pinch, 4 geser saat zoom
                            var ax = 0f
                            var ay = 0f
                            var target = -1L
                            var multi = false
                            var lastDist = 0f
                            var lastCen = Offset.Zero
                            do {
                                val ev = awaitPointerEvent()
                                val pr = ev.changes.filter { it.pressed }
                                if (pr.size >= 2) {
                                    multi = true
                                    mode = 3
                                    val a = pr[0].position
                                    val b = pr[1].position
                                    val dist = (a - b).getDistance()
                                    val cen = (a + b) / 2f
                                    if (lastDist > 0f) {
                                        val ns = (zoom * (dist / lastDist)).coerceIn(0.5f, 6f)
                                        val f = ns / zoom
                                        val cx = cen.x - w / 2f
                                        val cy = cen.y - h / 2f
                                        val np = Offset(
                                            cx * (1f - f) + pan.x * f + (cen.x - lastCen.x),
                                            cy * (1f - f) + pan.y * f + (cen.y - lastCen.y)
                                        )
                                        zoom = ns
                                        pan = clampPan(np, ns, w, h)
                                        say(t("zoom_hud") + " ${"%.1f".format(ns)}x")
                                    }
                                    lastDist = dist
                                    lastCen = cen
                                    ev.changes.forEach { it.consume() }
                                } else if (pr.size == 1) {
                                    lastDist = 0f
                                    val ch = pr[0]
                                    val d = ch.position - ch.previousPosition
                                    if (multi) {
                                        if (zoom > 1.01f) {
                                            pan = clampPan(pan + d, zoom, w, h)
                                            ch.consume()
                                        }
                                    } else if (zoomed0) {
                                        ax += d.x
                                        ay += d.y
                                        if (mode == 0 && maxOf(abs(ax), abs(ay)) > slop) mode = 4
                                        if (mode == 4) {
                                            pan = clampPan(pan + d, zoom, w, h)
                                            ch.consume()
                                        }
                                    } else {
                                        ax += d.x
                                        ay += d.y
                                        if (mode == 0 && maxOf(abs(ax), abs(ay)) > 24f) {
                                            mode = if (abs(ax) > abs(ay)) 1 else 2
                                        }
                                        if (mode == 1) {
                                            val du = player.duration.coerceAtLeast(0L)
                                            target = (startPos + (ax / w * 120000f).toLong()).coerceIn(0L, du)
                                            val diff = ((target - startPos) / 1000).toInt()
                                            say((if (diff >= 0) "+" else "−") + abs(diff) + " " + t("sec_unit") + " · " + fmtTime(target))
                                        } else if (mode == 2) {
                                            val dl = -ay / (h * 0.7f)
                                            if (left) {
                                                brLevel = (startLevel + dl * 160f).coerceIn(-100f, 100f)
                                                applyBrightness()
                                                say(t("bright_hud") + " " + fmtLevel(brLevel))
                                            } else {
                                                val f = (startVol + dl).coerceIn(0f, 1f)
                                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * maxVol).roundToInt(), 0)
                                                say(t("vol_hud") + " ${(f * 100).roundToInt()}%")
                                            }
                                        }
                                        if (mode != 0) ch.consume()
                                    }
                                }
                            } while (ev.changes.any { it.pressed })

                            if (multi || mode == 4) {
                                settleZoom()
                                return@awaitEachGesture
                            }
                            if (mode == 1 && target >= 0) {
                                player.seekTo(target)
                                return@awaitEachGesture
                            }
                            if (mode == 0 && maxOf(abs(ax), abs(ay)) < slop * 1.5f &&
                                System.currentTimeMillis() - t0 < 500
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - hold.tapAt < 300) {
                                    hold.tapJob?.cancel()
                                    hold.tapAt = 0L
                                    hold.seekAt = now
                                    val r = down.position.x / w
                                    when {
                                        r < 0.35f -> seekBy(-5000)
                                        r > 0.65f -> seekBy(5000)
                                        else -> toggle()
                                    }
                                } else {
                                    hold.tapAt = now
                                    hold.tapJob?.cancel()
                                    // Tap tunggal tepat setelah double-tap/seek diabaikan, supaya
                                    // double-tap beruntun buat skip jauh nggak memunculkan kontrol.
                                    hold.tapJob = scope.launch {
                                        delay(300)
                                        if (System.currentTimeMillis() - hold.seekAt > 700) {
                                            showUi = !showUi
                                            uiTick++
                                        }
                                    }
                                }
                            }
                        }
                    }
                )

                // 2. Kontrol
                if (!locked && showUi) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        // Kontrol tengah: jarak proporsional lebar layar.
                        // -5 / +5 di tepi kiri-kanan, tengah vertikal; prev/pause/next di tengah.
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wd = maxWidth
                            val space = (wd * 0.07f).coerceIn(14.dp, 56.dp)
                            val groupW = 46.dp + 64.dp + 46.dp + space * 2
                            val groupLeft = (wd - groupW) / 2
                            // 15% dari tepi, tapi dijaga supaya nggak nabrak kelompok tengah (portrait)
                            val seekPad = minOf(wd * 0.15f, groupLeft - 10.dp - 50.dp).coerceAtLeast(8.dp)

                            Row(
                                Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(space)
                            ) {
                                IconBtn(Ic.PREV) {
                                    if (player.currentPosition > 4000 || !player.hasPreviousMediaItem()) player.seekTo(0)
                                    else player.seekToPreviousMediaItem()
                                    uiTick++
                                }
                                IconBtn(if (wantPlay) Ic.PAUSE else Ic.PLAY, d = 64.dp, icon = 34.dp) { toggle(); uiTick++ }
                                IconBtn(Ic.NEXT) { player.seekToNextMediaItem(); uiTick++ }
                            }
                            Box(Modifier.align(Alignment.CenterStart).padding(start = seekPad)) {
                                SeekBtn(false) { seekBy(-5000); uiTick++ }
                            }
                            Box(Modifier.align(Alignment.CenterEnd).padding(end = seekPad)) {
                                SeekBtn(true) { seekBy(5000); uiTick++ }
                            }
                        }

                        // Bar atas
                        Row(
                            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Scrim)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBtn(Ic.BACK, bg = Color.Transparent) { finish() }
                            Text(
                                vidTitle, color = Color.White, fontSize = 15.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            )
                            IconBtn(Ic.INFO, bg = Color.Transparent) { infoRows = buildInfo() }
                            IconBtn(Ic.SUBS, bg = Color.Transparent) { subPicker.launch(arrayOf("*/*")) }
                            IconBtn(
                                when (fitI) {
                                    1 -> Ic.FIT_CROP
                                    2 -> Ic.FIT
                                    3 -> Ic.FIT_STRETCH
                                    else -> Ic.FIT_PAS
                                },
                                on = fitI != 0, bg = Color.Transparent
                            ) {
                                fitI = (fitI + 1) % FIT_KEYS.size
                                say(t(FIT_KEYS[fitI]))
                                uiTick++
                            }
                            if (Build.VERSION.SDK_INT >= 26) IconBtn(Ic.PIP, bg = Color.Transparent) { enterPip() }
                        }

                        // Bar bawah
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Scrim).padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fmtTime(if (stepping) scrub else pos), color = Color.White, fontSize = 12.sp)
                                SeekBar(
                                    pos = if (stepping) scrub else pos,
                                    dur = dur,
                                    stepping = stepping,
                                    onJump = { ms -> player.seekTo(ms); pos = ms; uiTick++ },
                                    onStep = { ms -> scrub = ms; player.seekTo(ms); uiTick++ },
                                    onStepping = { s ->
                                        if (s) scrub = pos
                                        stepping = s
                                    },
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                )
                                Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconBtn(Ic.LOCK, d = 40.dp, icon = 22.dp) { showUi = false; lock(false) }
                                Spacer(Modifier.width(8.dp))
                                IconBtn(Ic.PIN, d = 40.dp, icon = 22.dp) { showUi = false; lock(true) }
                                Spacer(Modifier.weight(1f))
                                val spd = SPEEDS[speedI]
                                val spdT = if (spd % 1f == 0f) "${spd.toInt()}x" else "${spd}x"
                                SpeedBtn(spdT, spd != 1f) {
                                    speedI = (speedI + 1) % SPEEDS.size
                                    player.playbackParameters = PlaybackParameters(SPEEDS[speedI])
                                    uiTick++
                                }
                                Spacer(Modifier.width(8.dp))
                                IconBtn(
                                    when (orient) {
                                        1 -> Ic.ROT_LAND
                                        2 -> Ic.ROT_PORT
                                        else -> Ic.ROTATE
                                    },
                                    on = orient != 0, d = 40.dp, icon = 22.dp
                                ) {
                                    orient = (orient + 1) % 3
                                    requestedOrientation = when (orient) {
                                        1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    }
                                    say(t(ORIENT_KEYS[orient]))
                                    uiTick++
                                }
                                Spacer(Modifier.width(8.dp))
                                IconBtn(Ic.REPEAT, on = loop, d = 40.dp, icon = 22.dp) {
                                    loop = !loop
                                    player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                    uiTick++
                                }
                            }
                        }
                    }
                }

                // 3. Lapisan redup (nggak nangkap sentuhan)
                if (dimAlpha > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
                }

                // 4. Notif singkat
                if (hud.isNotEmpty()) {
                    Text(
                        hud, color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xDD0C0F14))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 5. Tombol buka kunci: kecil, tahan 1,5 detik
                if (locked) {
                    Box(
                        Modifier.align(Alignment.TopEnd).safeDrawingPadding()
                            .padding(top = 22.dp, end = 22.dp).size(48.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    var timedOut = true
                                    withTimeoutOrNull(1500L) {
                                        waitForUpOrCancellation()
                                        timedOut = false
                                    }
                                    if (timedOut) {
                                        unlock()
                                        say(t("unlocked"))
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.alpha(0.4f)) { Icon24(Ic.LOCK, Color.White, 20.dp) }
                    }
                }

                // 6. Kartu info video
                infoRows?.let { rows -> InfoCard(rows) { infoRows = null } }
            }
        }
    }
}

private fun clampPan(t: Offset, s: Float, w: Float, h: Float): Offset {
    val mx = (w * (s - 1f) / 2f).coerceAtLeast(0f)
    val my = (h * (s - 1f) / 2f).coerceAtLeast(0f)
    return Offset(t.x.coerceIn(-mx, mx), t.y.coerceIn(-my, my))
}

private fun softScale(w: Int, h: Int, a: Float): Float {
    if (w <= 0 || h <= 0 || a <= 0f) return 1f
    val s = w.toFloat() / h
    return maxOf(s / a, a / s).coerceAtMost(1.15f)
}

// Seekbar custom.
// - Tap: loncat persis ke titik itu.
// - Tekan, tahan (350 ms), lalu geser: skip per 5 detik. Satu langkah per ~26 dp geseran,
//   nggak tergantung panjang durasi.
@Composable
private fun SeekBar(
    pos: Long,
    dur: Long,
    stepping: Boolean,
    onJump: (Long) -> Unit,
    onStep: (Long) -> Unit,
    onStepping: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val curPos by rememberUpdatedState(pos)
    val curDur by rememberUpdatedState(dur)
    val jump by rememberUpdatedState(onJump)
    val stepCb by rememberUpdatedState(onStep)
    val steppingCb by rememberUpdatedState(onStepping)
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f

    Box(
        modifier.height(36.dp).pointerInput(Unit) {
            val pad = 10.dp.toPx()
            val stepPx = 26.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                var timedOut = true
                var up: PointerInputChange? = null
                withTimeoutOrNull(350L) {
                    up = waitForUpOrCancellation()
                    timedOut = false
                }
                if (!timedOut) {
                    val u = up
                    if (u != null) {
                        val f = ((u.position.x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f)
                        if (curDur > 0) jump((f * curDur).toLong())
                    }
                    return@awaitEachGesture
                }
                // Tahan: mode langkah 5 detik
                steppingCb(true)
                var target = curPos
                var anchor = down.position.x
                var first = true
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    if (first) {
                        anchor = ch.position.x
                        first = false
                    }
                    val n = ((ch.position.x - anchor) / stepPx).toInt()
                    if (n != 0) {
                        target = (target + n * 5000L).coerceIn(0L, curDur)
                        anchor += n * stepPx
                        stepCb(target)
                    }
                    ch.consume()
                }
                steppingCb(false)
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 10.dp.toPx()
            val cy = size.height / 2f
            val x0 = pad
            val x1 = size.width - pad
            val th = 4.dp.toPx()
            drawRoundRect(Color(0x66FFFFFF), Offset(x0, cy - th / 2f), Size(x1 - x0, th), CornerRadius(th / 2f))
            val px = x0 + (x1 - x0) * frac
            drawRoundRect(Accent, Offset(x0, cy - th / 2f), Size(px - x0, th), CornerRadius(th / 2f))
            drawCircle(Accent, if (stepping) 10.dp.toPx() else 7.dp.toPx(), Offset(px, cy))
        }
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp).widthIn(max = 440.dp).fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp)).background(InfoBg)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon24(Ic.INFO, Accent, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(t("info_title"), color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(
                    t("close"), color = Accent, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(k, color = InfoMute, fontSize = 13.sp, modifier = Modifier.width(104.dp))
                        Text(v, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBtn(
    k: Ic,
    on: Boolean = false,
    d: Dp = 46.dp,
    icon: Dp = 26.dp,
    bg: Color = BtnBg,
    click: () -> Unit
) {
    Box(
        Modifier.size(d).clip(CircleShape).background(bg).clickable(onClick = click),
        contentAlignment = Alignment.Center
    ) { Icon24(k, if (on) Accent else Color.White, icon) }
}

@Composable
private fun SeekBtn(forward: Boolean, click: () -> Unit) {
    Box(
        Modifier.size(50.dp).clip(CircleShape).background(BtnBg).clickable(onClick = click),
        contentAlignment = Alignment.Center
    ) { SeekIcon(forward, Color.White, 32.dp) }
}

@Composable
private fun SpeedBtn(label: String, on: Boolean, click: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(BtnBg).clickable(onClick = click),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (on) Accent else Color.White, fontSize = 13.sp) }
}

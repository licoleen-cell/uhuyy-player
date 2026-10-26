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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)
private val FITS = arrayOf("Pas", "Crop ringan", "Isi layar", "Regang")
private val ORIENTS = arrayOf("Putar: auto", "Putar: datar", "Putar: tegak")
private val Blue: Color get() = ThemeState.cur.acc
private val Scrim = Color(0x99000000)

class PlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SharedPreferences
    private lateinit var audio: AudioManager
    private var locked by mutableStateOf(false)
    private var inPip by mutableStateOf(false)
    private var vidTitle by mutableStateOf("")
    private var aspect by mutableFloatStateOf(16f / 9f)
    private var brightness = 0.5f
    private var skipResume = false

    private val subPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) addSubtitle(u)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pos", MODE_PRIVATE)
        ThemeState.load(this)
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
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val cur = window.attributes.screenBrightness
        brightness = if (cur in 0f..1f) cur else runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0.05f, 1f)

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
                Toast.makeText(this@PlayerActivity, "Video ini nggak bisa diputar", Toast.LENGTH_SHORT).show()
            }
        })
        val start = intent.getIntExtra("index", 0).coerceIn(0, items.lastIndex)
        player.setMediaItems(items, start, C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue)) { PlayerScreen() }
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
        Toast.makeText(this, "Subtitle dipasang", Toast.LENGTH_SHORT).show()
    }

    // ---- Picture in Picture ----
    private fun pipParams(): PictureInPictureParams {
        val a = aspect.coerceIn(0.45f, 2.3f)
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational((a * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(player.isPlaying && !locked)
        return b.build()
    }

    private fun updatePip() {
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (!locked) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && ::player.isInitialized && player.isPlaying && !locked) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    // ---- Kunci anak: screen pinning bawaan Android ----
    private fun lock() {
        locked = true
        runCatching { startLockTask() }
        updatePip()
    }

    private fun unlock() {
        locked = false
        runCatching { stopLockTask() }
        updatePip()
    }

    private fun applyBrightness() {
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
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
        var playing by remember { mutableStateOf(true) }
        var pos by remember { mutableLongStateOf(0L) }
        var dur by remember { mutableLongStateOf(0L) }
        var speedI by remember { mutableIntStateOf(2) }
        var fitI by remember { mutableIntStateOf(0) }
        var orient by remember { mutableIntStateOf(0) }
        var loop by remember { mutableStateOf(false) }
        var hud by remember { mutableStateOf("") }
        var hudJob by remember { mutableStateOf<Job?>(null) }
        var seeking by remember { mutableStateOf(false) }
        var sliderVal by remember { mutableFloatStateOf(0f) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        fun say(t: String) {
            hud = t
            hudJob?.cancel()
            hudJob = scope.launch { delay(900); hud = "" }
        }

        fun toggle() {
            if (player.isPlaying) player.pause() else player.play()
        }

        fun seekBy(ms: Long) {
            val d = player.duration
            val t = (player.currentPosition + ms).coerceAtLeast(0L)
            player.seekTo(if (d > 0) t.coerceAtMost(d) else t)
            say(if (ms > 0) "+${ms / 1000} dtk" else "−${-ms / 1000} dtk")
        }

        BackHandler(enabled = locked) { }

        LaunchedEffect(Unit) {
            var tick = 0
            while (true) {
                pos = player.currentPosition
                dur = player.duration.coerceAtLeast(0L)
                playing = player.isPlaying
                tick++
                if (tick % 12 == 0) savePos()
                delay(250)
            }
        }
        LaunchedEffect(showUi, playing, uiTick, locked) {
            if (showUi && playing && !locked) {
                delay(3500)
                showUi = false
            }
        }
        LaunchedEffect(playing) { if (!playing && !locked) showUi = true }

        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                // Lapisan gestur
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(locked) {
                            if (locked) return@pointerInput
                            detectTapGestures(
                                onTap = { showUi = !showUi; uiTick++ },
                                onDoubleTap = { o ->
                                    val r = o.x / size.width
                                    when {
                                        r < 0.35f -> seekBy(-5000)
                                        r > 0.65f -> seekBy(5000)
                                        else -> toggle()
                                    }
                                }
                            )
                        }
                        .pointerInput(locked) {
                            if (locked) return@pointerInput
                            var mode = 0
                            var ax = 0f
                            var ay = 0f
                            var left = true
                            var startPos = 0L
                            var startBr = 0.5f
                            var startVol = 0f
                            var target = -1L
                            detectDragGestures(
                                onDragStart = { o ->
                                    mode = 0; ax = 0f; ay = 0f; target = -1L
                                    left = o.x < size.width / 2f
                                    startPos = player.currentPosition
                                    startBr = brightness
                                    startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                },
                                onDragEnd = { if (mode == 1 && target >= 0) player.seekTo(target) },
                                onDragCancel = { },
                                onDrag = { _, d ->
                                    ax += d.x
                                    ay += d.y
                                    if (mode == 0 && maxOf(abs(ax), abs(ay)) > 24f) {
                                        mode = if (abs(ax) > abs(ay)) 1 else 2
                                    }
                                    if (mode == 1) {
                                        val du = player.duration.coerceAtLeast(0L)
                                        target = (startPos + (ax / size.width * 120000f).toLong()).coerceIn(0L, du)
                                        val diff = ((target - startPos) / 1000).toInt()
                                        say((if (diff >= 0) "+" else "−") + abs(diff) + " dtk · " + fmtTime(target))
                                    } else if (mode == 2) {
                                        val dl = -ay / (size.height * 0.7f)
                                        if (left) {
                                            brightness = (startBr + dl).coerceIn(0.03f, 1f)
                                            applyBrightness()
                                            say("Terang ${(brightness * 100).roundToInt()}%")
                                        } else {
                                            val f = (startVol + dl).coerceIn(0f, 1f)
                                            val mx = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * mx).roundToInt(), 0)
                                            say("Volume ${(f * 100).roundToInt()}%")
                                        }
                                    }
                                }
                            )
                        }
                )

                if (hud.isNotEmpty()) {
                    Text(
                        hud, color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xDD0C0F14))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (locked) {
                    // Tombol buka kunci: kecil, tahan 1,5 detik
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
                                        say("Kunci dibuka")
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔓", fontSize = 15.sp, modifier = Modifier.alpha(0.4f))
                    }
                } else if (showUi) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Row(
                            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Scrim).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("←", color = Color.White, fontSize = 24.sp,
                                modifier = Modifier.clickable { finish() }.padding(horizontal = 12.dp, vertical = 4.dp))
                            Text(vidTitle, color = Color.White, fontSize = 15.sp, maxLines = 1,
                                modifier = Modifier.weight(1f))
                        }

                        Row(
                            Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Big("⏮") {
                                if (player.currentPosition > 4000 || !player.hasPreviousMediaItem()) player.seekTo(0)
                                else player.seekToPreviousMediaItem()
                                uiTick++
                            }
                            Big("−5") { seekBy(-5000); uiTick++ }
                            Big(if (playing) "⏸" else "▶") { toggle(); uiTick++ }
                            Big("+5") { seekBy(5000); uiTick++ }
                            Big("⏭") { player.seekToNextMediaItem(); uiTick++ }
                        }

                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Scrim).padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fmtTime(if (seeking) (sliderVal * dur).toLong() else pos), color = Color.White, fontSize = 12.sp)
                                Slider(
                                    value = if (seeking) sliderVal else if (dur > 0) pos.toFloat() / dur else 0f,
                                    onValueChange = { seeking = true; sliderVal = it; uiTick++ },
                                    onValueChangeFinished = {
                                        player.seekTo((sliderVal * dur).toLong())
                                        seeking = false
                                    },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text(fmtTime(dur), color = Color.White, fontSize = 12.sp)
                            }
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val sp = SPEEDS[speedI]
                                val spT = if (sp % 1f == 0f) "${sp.toInt()}x" else "${sp}x"
                                Chip(spT, sp != 1f) {
                                    speedI = (speedI + 1) % SPEEDS.size
                                    player.playbackParameters = PlaybackParameters(SPEEDS[speedI])
                                    uiTick++
                                }
                                Chip(FITS[fitI]) { fitI = (fitI + 1) % FITS.size; say(FITS[fitI]); uiTick++ }
                                Chip(ORIENTS[orient]) {
                                    orient = (orient + 1) % 3
                                    requestedOrientation = when (orient) {
                                        1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    }
                                    uiTick++
                                }
                                Chip("Subtitle") { subPicker.launch(arrayOf("*/*")) }
                                Chip("Ulang", loop) {
                                    loop = !loop
                                    player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                    uiTick++
                                }
                                Chip("PiP") { enterPip() }
                                Chip("Kunci") { showUi = false; lock() }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun softScale(w: Int, h: Int, a: Float): Float {
    if (w <= 0 || h <= 0 || a <= 0f) return 1f
    val s = w.toFloat() / h
    return maxOf(s / a, a / s).coerceAtMost(1.15f)
}

@Composable
private fun Chip(t: String, on: Boolean = false, click: () -> Unit) {
    Text(
        t, color = if (on) Blue else Color.White, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC161B23))
            .clickable(onClick = click).padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

@Composable
private fun Big(t: String, click: () -> Unit) {
    Text(
        t, color = Color.White, fontSize = 28.sp,
        modifier = Modifier.clip(RoundedCornerShape(40.dp)).background(Scrim)
            .clickable(onClick = click).padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

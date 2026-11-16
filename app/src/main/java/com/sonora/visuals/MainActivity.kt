package com.sonora.visuals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.*

private val Ink = Color(0xFF080B14)
private val Panel = Color(0xFF121827)
private val Cyan = Color(0xFF4DEBFF)
private val Violet = Color(0xFF9B7BFF)
private val Lime = Color(0xFFB6FF6B)

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val waveform = mutableStateListOf<Float>()
    private val spectrum = mutableStateListOf<Float>()
    private var playing by mutableStateOf(false)
    private var trackName by mutableStateOf("No audio selected")
    private var durationMs by mutableIntStateOf(0)
    private var positionMs by mutableIntStateOf(0)
    private var bpm by mutableIntStateOf(0)
    private var style by mutableStateOf("Spectrum")
    private var permissionReady = false
    private var lastBeatAt = 0L
    private val beatIntervals = ArrayDeque<Long>()
    private var lastEnergy = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionReady = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        setContent { SonoraScreen() }
    }

    private fun loadAudio(uri: Uri) {
        releaseAudio()
        trackName = queryName(uri)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(this, uri)
            mp.setOnPreparedListener {
                durationMs = it.duration
                it.start()
                playing = true
                attachVisualizer(it.audioSessionId)
            }
            mp.setOnCompletionListener { playing = false; positionMs = durationMs }
            mp.prepareAsync()
        } catch (e: Exception) {
            trackName = "Unable to open audio: ${e.localizedMessage ?: "unknown error"}"
            releaseAudio()
        }
    }

    private fun queryName(uri: Uri): String {
        var result = "Imported audio"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) result = it.getString(0) ?: result
        }
        return result
    }

    private fun attachVisualizer(sessionId: Int) {
        if (!permissionReady || sessionId == 0) return
        try {
            visualizer?.release()
            val v = Visualizer(sessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(effect: Visualizer, bytes: ByteArray, samplingRate: Int) {
                    val points = bytes.map { ((it.toInt() and 0xff) - 128) / 128f }
                    waveform.clear()
                    waveform.addAll(points)
                }
                override fun onFftDataCapture(effect: Visualizer, bytes: ByteArray, samplingRate: Int) {
                    val bins = ArrayList<Float>()
                    var energy = 0f
                    for (i in 2 until bytes.size step 2) {
                        val re = bytes[i].toInt()
                        val im = if (i + 1 < bytes.size) bytes[i + 1].toInt() else 0
                        val magnitude = sqrt((re * re + im * im).toFloat()).coerceAtMost(255f) / 255f
                        bins.add(magnitude)
                        energy += magnitude
                    }
                    spectrum.clear()
                    spectrum.addAll(bins.take(96))
                    val now = System.currentTimeMillis()
                    val lowEnergy = bins.take(8).average().toFloat()
                    if (lowEnergy > 0.24f && lowEnergy > lastEnergy * 1.42f && now - lastBeatAt > 260) {
                        if (lastBeatAt > 0L) {
                            val interval = now - lastBeatAt
                            if (interval in 300..1500) {
                                beatIntervals.addLast(interval)
                                while (beatIntervals.size > 8) beatIntervals.removeFirst()
                                bpm = (60000.0 / beatIntervals.average()).roundToInt()
                            }
                        }
                        lastBeatAt = now
                    }
                    lastEnergy = lowEnergy * 0.72f + lastEnergy * 0.28f
                }
            }, Visualizer.getMaxCaptureRate(), true, true)
            v.enabled = true
            visualizer = v
        } catch (_: Exception) { /* Audio playback remains available if capture is restricted. */ }
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); playing = false }
        else { p.start(); playing = true }
    }

    private fun releaseAudio() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        try { player?.release() } catch (_: Exception) {}
        player = null
        playing = false
        positionMs = 0
        durationMs = 0
        bpm = 0
        beatIntervals.clear()
        lastBeatAt = 0L
    }

    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    @Composable
    private fun SonoraScreen() {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                try { contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                loadAudio(it)
            }
        }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionReady = it
        }
        LaunchedEffect(Unit) {
            if (!permissionReady) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        LaunchedEffect(player, playing) {
            while (true) {
                if (player != null) {
                    try { positionMs = player?.currentPosition ?: 0 } catch (_: Exception) {}
                }
                delay(80)
            }
        }

        MaterialTheme(colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Cyan, secondary = Violet,
            onBackground = Color.White, onSurface = Color.White
        )) {
            Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SONORA", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("VISUALS", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                    Surface(color = Color(0xFF1B2638), shape = RoundedCornerShape(50)) {
                        Text("BETA 0.1", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("NOW PLAYING", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                Text(trackName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Text(if (playing) "● LIVE" else "○ READY", color = if (playing) Lime else Color.LightGray, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(positionMs), color = Color.LightGray, fontSize = 10.sp)
                            Slider(
                                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                                onValueChange = { value -> try { player?.seekTo((value * durationMs).toInt()) } catch (_: Exception) {} },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan)
                            )
                            Text(formatTime(durationMs), color = Color.LightGray, fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { picker.launch(arrayOf("audio/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
                                Text("＋ IMPORT AUDIO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { togglePlayback() }, enabled = player != null) {
                                Text(if (playing) "PAUSE" else "PLAY", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("TEMPO", if (bpm > 0) "$bpm BPM" else "— BPM", Modifier.weight(1f))
                    MetricCard("ANALYSIS", if (visualizer != null) "LIVE FFT" else "WAITING", Modifier.weight(1f))
                    MetricCard("FRAME RATE", "60 FPS", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("VISUALIZER STUDIO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Text("AUDIO REACTIVE", color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Surface(color = Color(0xFF0D1320), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        AudioVizView(waveform.toList(), spectrum.toList(), style, Modifier.fillMaxWidth().weight(1f))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Spectrum", "Wave", "Circular", "Mirror", "Pulse", "Neon").forEach { item ->
                                FilterChip(selected = style == item, onClick = { style = item }, label = { Text(item, fontSize = 11.sp) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), enabled = false) { Text("PREVIEW READY", fontSize = 11.sp) }
                    Button(onClick = {}, modifier = Modifier.weight(1f), enabled = false, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                        Text("EXPORT MP4 · 1080×1920", fontSize = 10.sp)
                    }
                }
                Text("Import audio to activate real-time waveform + frequency analysis.", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), color = Color.Gray, fontSize = 10.sp)
            }
        }
    }

    @Composable
    private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = modifier) {
            Column(Modifier.padding(11.dp)) {
                Text(label, color = Color.Gray, fontSize = 8.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun AudioVizView(wave: List<Float>, fft: List<Float>, mode: String, modifier: Modifier = Modifier) {
        AndroidView(modifier = modifier, factory = { ctx -> VisualizerCanvas(ctx) }, update = { it.update(wave, fft, mode) })
    }

    override fun onDestroy() {
        releaseAudio()
        super.onDestroy()
    }
}

private class VisualizerCanvas(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wave: List<Float> = emptyList()
    private var fft: List<Float> = emptyList()
    private var mode = "Spectrum"
    private val cyan = android.graphics.Color.rgb(77, 235, 255)
    private val purple = android.graphics.Color.rgb(155, 123, 255)
    private val green = android.graphics.Color.rgb(182, 255, 107)

    fun update(w: List<Float>, f: List<Float>, m: String) {
        wave = w; fft = f; mode = m; invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x223D536B
        for (i in 1..4) canvas.drawLine(0f, h * i / 5f, w, h * i / 5f, paint)
        when (mode) {
            "Wave" -> drawWave(canvas, w, h)
            "Circular" -> drawCircle(canvas, w, h)
            "Mirror" -> drawMirror(canvas, w, h)
            "Pulse" -> drawPulse(canvas, w, h)
            "Neon" -> drawNeon(canvas, w, h)
            else -> drawSpectrum(canvas, w, h)
        }
    }

    private fun drawSpectrum(c: Canvas, w: Float, h: Float) {
        val values = fft.ifEmpty { List(64) { 0.03f } }
        val count = min(values.size, 72)
        val gap = 3f
        val bw = (w - gap * (count - 1)) / count
        for (i in 0 until count) {
            val v = values[i].coerceIn(0f, 1f)
            val bh = max(3f, v * h * 0.86f)
            paint.style = Paint.Style.FILL
            paint.shader = android.graphics.LinearGradient(0f, h - bh, 0f, h, cyan, purple, android.graphics.Shader.TileMode.CLAMP)
            val left = i * (bw + gap)
            c.drawRoundRect(RectF(left, h - bh, left + bw, h), bw / 2, bw / 2, paint)
        }
        paint.shader = null
    }

    private fun drawWave(c: Canvas, w: Float, h: Float) {
        val values = wave.ifEmpty { List(128) { 0f } }
        val path = android.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = i * w / max(1, values.size - 1)
            val y = h / 2f + v * h * 0.42f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = cyan
        c.drawPath(path, paint)
        paint.color = 0x665DEBFF; paint.strokeWidth = 8f; c.drawPath(path, paint)
    }

    private fun drawCircle(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f; val radius = min(w, h) * 0.23f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.color = cyan
        c.drawCircle(cx, cy, radius, paint)
        val values = fft.ifEmpty { List(48) { 0.02f } }
        values.take(72).forEachIndexed { i, v ->
            val a = (i.toFloat() / values.size) * Math.PI * 2
            val r1 = radius + 4
            val r2 = r1 + v.coerceIn(0f, 1f) * min(w, h) * 0.23f
            c.drawLine(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1, cx + cos(a).toFloat() * r2, cy + sin(a).toFloat() * r2, paint)
        }
    }

    private fun drawMirror(c: Canvas, w: Float, h: Float) {
        val values = fft.ifEmpty { List(48) { 0.02f } }
        val mid = h / 2f; val bw = w / values.size
        values.forEachIndexed { i, v ->
            val bh = v.coerceIn(0f, 1f) * h * 0.38f
            paint.style = Paint.Style.FILL; paint.color = if (i % 2 == 0) cyan else purple
            c.drawRoundRect(i * bw + 1, mid - bh, (i + 1) * bw - 1, mid + bh, 4f, 4f, paint)
        }
    }

    private fun drawPulse(c: Canvas, w: Float, h: Float) {
        val energy = fft.take(12).average().toFloat().coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = cyan
        for (i in 0..3) c.drawCircle(w / 2, h / 2, min(w, h) * (0.12f + i * 0.13f + energy * 0.06f), paint)
        drawWave(c, w, h)
    }

    private fun drawNeon(c: Canvas, w: Float, h: Float) {
        drawSpectrum(c, w, h)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = green
        c.drawLine(0f, h * 0.84f, w, h * 0.84f, paint)
    }
}

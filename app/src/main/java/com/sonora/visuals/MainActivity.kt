package com.sonora.visuals

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.*

private val Bg = Color(0xFF070914)
private val Panel = Color(0xFF11182A)
private val Cyan = Color(0xFF5CF4FF)
private val Violet = Color(0xFFAB75FF)
private val Pink = Color(0xFFFF5CA8)

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private val fft = mutableStateListOf<Float>()
    private val wave = mutableStateListOf<Float>()
    private var playing by mutableStateOf(false)
    private var track by mutableStateOf("Import a song to begin")
    private var duration by mutableIntStateOf(0)
    private var position by mutableIntStateOf(0)
    private var captureAvailable by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Studio() }
    }

    private fun load(uri: Uri) {
        release()
        track = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) ?: "Imported audio" else "Imported audio"
        } ?: "Imported audio"
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(this, uri)
            mp.setOnPreparedListener { p ->
                duration = p.duration
                p.start()
                playing = true
                attach(p.audioSessionId)
            }
            mp.setOnCompletionListener { playing = false; position = duration }
            mp.prepareAsync()
        } catch (e: Exception) { track = "Could not open audio: ${e.localizedMessage}"; release() }
    }

    private fun attach(session: Int) {
        if (session == 0 || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            val v = Visualizer(session)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(effect: Visualizer, bytes: ByteArray, samplingRate: Int) {
                    wave.clear()
                    wave.addAll(bytes.map { ((it.toInt() and 255) - 128) / 128f })
                }
                override fun onFftDataCapture(effect: Visualizer, bytes: ByteArray, samplingRate: Int) {
                    val raw = (2 until bytes.size step 2).map { i ->
                        val re = bytes[i].toInt()
                        val im = if (i + 1 < bytes.size) bytes[i + 1].toInt() else 0
                        (sqrt((re * re + im * im).toFloat()) / 180f).coerceIn(0f, 1f)
                    }
                    val count = 96
                    val logBins = (0 until count).map { n ->
                        val a = (2.0.pow(n.toDouble() / count * ln(max(2, raw.size).toDouble()) / ln(2.0))).toInt().coerceIn(0, raw.lastIndex.coerceAtLeast(0))
                        raw.getOrElse(a) { 0f }
                    }
                    fft.clear()
                    fft.addAll(logBins)
                    captureAvailable = true
                }
            }, Visualizer.getMaxCaptureRate(), true, true)
            v.enabled = true
            visualizer = v
        } catch (_: Exception) { captureAvailable = false }
    }

    private fun release() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        try { player?.release() } catch (_: Exception) {}
        player = null
        playing = false
        position = 0; duration = 0; captureAvailable = false
        fft.clear(); wave.clear()
    }

    @Composable
    private fun Studio() {
        var style by remember { mutableStateOf("Circular Spectrum") }
        var sensitivity by remember { mutableFloatStateOf(1.25f) }
        var glow by remember { mutableFloatStateOf(0.75f) }
        var bands by remember { mutableFloatStateOf(72f) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri ->
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            load(uri)
        } }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.RECORD_AUDIO)
        }
        LaunchedEffect(playing) {
            while (true) {
                if (playing) position = try { player?.currentPosition ?: position } catch (_: Exception) { position }
                delay(100)
            }
        }
        MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)) {
            Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SONORA / MOTION LAB", color = Cyan, fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold)
                        Text("Visuals Studio", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Surface(color = Color(0xFF19243A), shape = RoundedCornerShape(50)) {
                        Text("AE INSPIRED", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("NOW PLAYING", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text(track, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Spacer(Modifier.height(9.dp))
                        Slider(value = if (duration > 0) position.toFloat() / duration else 0f, onValueChange = { if (duration > 0) { position = (it * duration).toInt(); player?.seekTo(position) } })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(time(position), color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(time(duration), color = Color.LightGray, fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { picker.launch(arrayOf("audio/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)) { Text("＋ IMPORT AUDIO") }
                            OutlinedButton(onClick = { val p = player ?: return@OutlinedButton; if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true } }, enabled = player != null) { Text(if (playing) "PAUSE" else "PLAY") }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("COMPOSITION", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (captureAvailable) "● LIVE FFT" else "○ WAITING FOR AUDIO", color = if (captureAvailable) Cyan else Color.Gray, fontSize = 9.sp)
                }
                Spacer(Modifier.height(9.dp))
                Surface(color = Color(0xFF090E1B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.padding(10.dp)) {
                        MotionPreview(fft.toList(), wave.toList(), style, sensitivity, glow, bands.toInt(), Modifier.fillMaxWidth().weight(1f))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Circular Spectrum", "Audio Spectrum", "Waveform", "Particles", "Neon Bars", "Beat Pulse").forEach {
                                FilterChip(selected = style == it, onClick = { style = it }, label = { Text(it, fontSize = 10.sp) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        SettingSlider("SENSITIVITY", sensitivity, 0.5f..2.5f, { sensitivity = it }, "${"%.1f".format(sensitivity)}×")
                        SettingSlider("GLOW", glow, 0f..1f, { glow = it }, "${(glow * 100).toInt()}%")
                        SettingSlider("FREQUENCY BANDS", bands, 24f..96f, { bands = it }, bands.toInt().toString())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), enabled = false) { Text("PREVIEW", fontSize = 11.sp) }
                    Button(onClick = {}, modifier = Modifier.weight(1f), enabled = false, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("EXPORT MP4 · NEXT BUILD", fontSize = 10.sp) }
                }
                Text("Live composition preview · Export pipeline is not enabled in this build.", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(top = 7.dp, bottom = 10.dp))
            }
        }
    }

    @Composable private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit, readout: String) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.LightGray, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Text(readout, color = Cyan, fontSize = 10.sp)
            }
            Slider(value = value, onValueChange = change, valueRange = range)
        }
    }

    override fun onDestroy() { release(); super.onDestroy() }
}

private fun time(ms: Int): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
private fun MotionPreview(fft: List<Float>, wave: List<Float>, style: String, sensitivity: Float, glow: Float, bands: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFF111A31), Color(0xFF070914))))) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h * 0.43f
        val minSide = min(w, h)
        val values = (0 until bands.coerceIn(8, 96)).map { i ->
            val raw = fft.getOrElse((i * max(1, fft.size) / bands.coerceAtLeast(1)).coerceIn(0, max(0, fft.lastIndex))) { 0.02f }
            (raw * sensitivity).coerceIn(0.015f, 1f)
        }
        val energy = values.take(12).average().toFloat().coerceIn(0f, 1f)
        val haloRadius = minSide * (0.12f + energy * 0.045f)
        drawCircle(Brush.radialGradient(listOf(Color(0x665D3EFF), Color(0x225D3EFF), Color.Transparent), center = Offset(cx, cy), radius = minSide * 0.48f), minSide * 0.48f, Offset(cx, cy))
        when (style) {
            "Circular Spectrum" -> {
                drawCircle(Color(0x665CF4FF), minSide * 0.205f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
                values.forEachIndexed { i, v ->
                    val a = (i.toFloat() / values.size * 2f * PI - PI / 2).toFloat()
                    val r1 = minSide * 0.22f
                    val r2 = r1 + v * minSide * 0.17f
                    val start = Offset(cx + cos(a) * r1, cy + sin(a) * r1)
                    val end = Offset(cx + cos(a) * r2, cy + sin(a) * r2)
                    drawLine(Color(0x665CF4FF), start, end, strokeWidth = (5f * glow + 1f).dp.toPx())
                    drawLine(Brush.linearGradient(listOf(Cyan, Violet, Pink)), start, end, strokeWidth = 2.dp.toPx())
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFF283D68), Color(0xFF11182A), Color(0xFF070914)), center = Offset(cx, cy), radius = minSide * 0.19f), minSide * 0.19f, Offset(cx, cy))
                drawCircle(Color(0xFF8AA6D8).copy(alpha = .45f), minSide * .19f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint(3).apply { color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.CENTER; textSize = minSide * .047f; typeface = android.graphics.Typeface.create("sans-serif-medium", 1) }
                    drawText("YOUR TRACK", cx, cy + minSide * .012f, p)
                    p.textSize = minSide * .024f; p.color = 0xFF8FA5C8.toInt()
                    drawText("SONORA VISUALS", cx, cy + minSide * .055f, p)
                }
            }
            "Audio Spectrum", "Neon Bars" -> {
                val bw = w / (values.size * 1.35f)
                values.forEachIndexed { i, v ->
                    val x = i * w / values.size
                    val bh = v * h * .55f
                    val top = h * .79f - bh
                    drawLine(Color(0x665CF4FF), Offset(x + bw / 2, h * .79f), Offset(x + bw / 2, top), strokeWidth = (bw * 1.7f * glow).coerceAtLeast(1f))
                    drawLine(Brush.verticalGradient(listOf(Pink, Violet, Cyan), top, h * .79f), Offset(x + bw / 2, h * .79f), Offset(x + bw / 2, top), strokeWidth = bw.coerceAtLeast(1f))
                }
            }
            "Waveform" -> {
                val pts = wave.ifEmpty { List(100) { sin(it.toDouble()).toFloat() * .08f } }
                val path = Path()
                pts.forEachIndexed { i, v ->
                    val x = i * w / max(1, pts.size - 1)
                    val y = cy + v * h * .62f * sensitivity
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color(0x665CF4FF), style = Stroke(width = 9.dp.toPx() * glow.coerceAtLeast(.1f)))
                drawPath(path, Brush.horizontalGradient(listOf(Cyan, Violet, Pink)), style = Stroke(width = 2.5.dp.toPx()))
            }
            "Particles" -> {
                values.forEachIndexed { i, v ->
                    val angle = i.toFloat() / values.size * 2f * PI
                    val radius = minSide * (.12f + v * .27f)
                    val x = cx + cos(angle) * radius
                    val y = cy + sin(angle) * radius
                    drawCircle(Brush.radialGradient(listOf(Cyan, Violet.copy(alpha = .1f), Color.Transparent), Offset(x, y), (3f + v * 9f).dp.toPx()), (3f + v * 9f).dp.toPx(), Offset(x, y))
                }
            }
            else -> {
                repeat(5) { i ->
                    val radius = haloRadius + i * minSide * .075f
                    drawCircle(Color(0xFF6C8BFF).copy(alpha = (.38f - i * .06f).coerceAtLeast(.04f) * glow), radius, Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
                }
                val pts = wave.ifEmpty { List(80) { 0f } }
                val path = Path()
                pts.forEachIndexed { i, v ->
                    val x = i * w / max(1, pts.size - 1)
                    val y = cy + v * h * .42f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Brush.horizontalGradient(listOf(Cyan, Violet, Pink)), style = Stroke(width = 3.dp.toPx()))
            }
        }
        drawLine(Color.White.copy(alpha = .06f), Offset(0f, h * .9f), Offset(w, h * .9f), 1.dp.toPx())
    }
}

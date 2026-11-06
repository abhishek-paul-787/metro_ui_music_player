package com.metro.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metro.music.audio.Player
import com.metro.music.data.AppSettings

@Composable
fun MetroToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().metroPress({ onChange(!on) }).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(label, 20, FontWeight.Normal) }
        T(if (on) "On" else "Off", 18, FontWeight.Normal, Metro.sub, Modifier.padding(end = 12.dp))
        Box(Modifier.size(52.dp, 24.dp).border(2.dp, Metro.fg).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.fillMaxHeight().width(if (on) 24.dp else 12.dp).background(if (on) Metro.accent else Metro.fg))
        }
    }
}

@Composable
fun VSlider(value: Float, min: Float, max: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    fun pos(y: Float, h: Int) = (max - (y / h).coerceIn(0f, 1f) * (max - min))
    Box(
        modifier.pointerInput(Unit) { detectTapGestures { onChange(pos(it.y, size.height)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> onChange(pos(c.position.y, size.height)) } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxHeight().width(3.dp).background(Metro.sub.copy(alpha = 0.5f)))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val frac = (max - value) / (max - min)
            val zero = (max - 0f) / (max - min)
            val top = maxHeight * minOf(frac, zero); val h = maxHeight * kotlin.math.abs(frac - zero)
            Box(Modifier.align(Alignment.TopCenter).offset(y = top).width(3.dp).height(h).background(Metro.accent))
            Box(Modifier.align(Alignment.TopCenter).offset(y = maxHeight * frac - 5.dp).size(20.dp, 10.dp).background(Metro.fg))
        }
    }
}

private val bandLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
private val presets = listOf(
    "flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "treble" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f),
    "vocal" to listOf(-2f, -2f, -1f, 1f, 3f, 3f, 2f, 0f, -1f, -2f),
    "rock" to listOf(4f, 3f, 1f, -1f, -2f, 0f, 2f, 3f, 4f, 4f),
)

@Composable
fun SettingsScreen() {
    val pager = rememberPagerState { 3 }
    val st by Player.status.collectAsState()
    Column(Modifier.fillMaxSize().background(Metro.bg).statusBarsPadding().navigationBarsPadding()) {
        T("METRO MUSIC", 14, FontWeight.SemiBold, Metro.fg, Modifier.padding(start = 22.dp, top = 14.dp))
        Pivot(listOf("output", "equalizer", "theme"), pager) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                when (page) {
                    0 -> {
                        T("upsample to (max)", 16, FontWeight.Normal, Metro.sub)
                        Spacer(Modifier.height(8.dp))
                        val opts = listOf(0 to "native", 48000 to "48 kHz", 96000 to "96 kHz", 192000 to "192 kHz", 384000 to "384 kHz")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            opts.take(3).forEach { (v, l) -> RateTile(l, AppSettings.rateCap == v, Modifier.weight(1f)) { AppSettings.rateCap = v; Player.reapplyOutput() } }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            opts.drop(3).forEach { (v, l) -> RateTile(l, AppSettings.rateCap == v, Modifier.weight(1f)) { AppSettings.rateCap = v; Player.reapplyOutput() } }
                            Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        T("44.1k-family files upsample to 44.1k multiples (e.g. 176.4 kHz), 48k-family to 48k multiples. Files already at or above the chosen rate are left untouched.", 14, FontWeight.Normal, Metro.sub, lines = 6)
                        Spacer(Modifier.height(18.dp))
                        MetroToggle("exclusive output (bit-perfect path)", AppSettings.exclusive) { AppSettings.exclusive = it; Player.reapplyOutput() }
                        Spacer(Modifier.height(18.dp))
                        T("current stream", 16, FontWeight.Normal, Metro.sub)
                        val s = st.src
                        if (s == null) T("nothing playing", 20, FontWeight.Light)
                        else {
                            T("source: ${s.mime.substringAfter('/')} · ${s.rate} Hz · ${s.channels} ch", 18, FontWeight.Light)
                            T("output: ${st.outRate} Hz · ${if (st.exclusive) "exclusive" else "shared"} · burst ${st.burst}", 18, FontWeight.Light)
                            T(if (st.upsampled) "resampler: 64-tap Kaiser sinc active" else "resampler: bypassed", 18, FontWeight.Light, Metro.accent)
                        }
                    }
                    1 -> {
                        MetroToggle("equalizer", AppSettings.eqOn) { AppSettings.eqOn = it; Player.applyEq() }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            presets.forEach { (n, g) ->
                                T(n, 18, FontWeight.Normal, Metro.accent, Modifier.clickable {
                                    g.forEachIndexed { i, v -> AppSettings.setGain(i, v) }; AppSettings.eqOn = true; Player.applyEq()
                                })
                            }
                        }
                        T("preamp ${"%+.1f".format(AppSettings.preamp)} dB", 16, FontWeight.Normal, Metro.sub)
                        SeekBar((AppSettings.preamp + 12f) / 24f) { AppSettings.preamp = it * 24f - 12f; Player.applyEq() }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (i in 0 until 10) Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                T("%+.0f".format(AppSettings.eqGains[i]), 12, FontWeight.Normal, Metro.sub)
                                VSlider(AppSettings.eqGains[i], -12f, 12f, Modifier.weight(1f).fillMaxWidth()) {
                                    AppSettings.setGain(i, Math.round(it).toFloat()); Player.applyEq()
                                }
                                T(bandLabels[i], 12, FontWeight.Normal, Metro.sub)
                            }
                        }
                    }
                    else -> {
                        MetroToggle("dark theme", AppSettings.dark) { AppSettings.dark = it }
                        Spacer(Modifier.height(12.dp))
                        T("accent colour", 16, FontWeight.Normal, Metro.sub)
                        Spacer(Modifier.height(8.dp))
                        Metro.accents.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { c ->
                                    Box(Modifier.weight(1f).aspectRatio(1f).background(Color(c))
                                        .then(if (AppSettings.accent == c) Modifier.border(3.dp, Metro.fg) else Modifier)
                                        .metroPress({ AppSettings.accent = c }))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RateTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(64.dp).background(if (selected) Metro.accent else Metro.tile).metroPress(onClick),
        contentAlignment = Alignment.Center,
    ) { T(label, 18, FontWeight.Normal, if (selected) Color.White else Metro.fg) }
}

package com.metro.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metro.music.audio.Player

@Composable
fun SeekBar(value: Float, onSeek: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val v = drag ?: value
    Box(
        Modifier.fillMaxWidth().height(36.dp)
            .pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null }, onDragCancel = { drag = null },
                ) { c, _ -> drag = (c.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Metro.sub.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(v.coerceIn(0f, 1f)).height(3.dp).background(Metro.accent))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.offset(x = maxWidth * v.coerceIn(0f, 1f) - 4.dp).size(8.dp, 24.dp).background(Metro.fg))
        }
    }
}

@Composable
fun NowPlayingScreen() {
    val t by Player.current.collectAsState()
    val playing by Player.isPlaying.collectAsState()
    val pos by Player.positionMs.collectAsState()
    val shuffle by Player.shuffle.collectAsState()
    val repeat by Player.repeat.collectAsState()
    val st by Player.status.collectAsState()
    val bm by rememberArt(t, 600)
    Box(Modifier.fillMaxSize().background(Metro.bg)) {
        bm?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().blur(40.dp).alpha(0.35f), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            T("NOW PLAYING", 14, FontWeight.SemiBold, Metro.fg, Modifier.padding(top = 14.dp, bottom = 12.dp))
            Art(t, Modifier.fillMaxWidth().aspectRatio(1f), 800)
            Spacer(Modifier.height(18.dp))
            T(t?.title ?: "", 30, FontWeight.Light, Metro.fg)
            T(t?.artist ?: "", 20, FontWeight.Normal, Metro.accent)
            T(t?.album ?: "", 15, FontWeight.Normal, Metro.sub)
            Spacer(Modifier.height(10.dp))
            val dur = t?.durationMs ?: 1L
            SeekBar((pos.toFloat() / dur)) { Player.seekTo((it * dur).toLong()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                T(fmt(pos), 14, FontWeight.Normal, Metro.sub); T(fmt(dur), 14, FontWeight.Normal, Metro.sub)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Ico.Shuffle, { Player.toggleShuffle() }, 44.dp, active = shuffle)
                CircleButton(Ico.Prev, { Player.prev() }, 52.dp)
                CircleButton(if (playing) Ico.Pause else Ico.Play, { Player.toggle() }, 72.dp)
                CircleButton(Ico.Next, { Player.next() }, 52.dp)
                CircleButton(if (repeat == 2) Ico.RepeatOne else Ico.Repeat, { Player.cycleRepeat() }, 44.dp, active = repeat != 0)
            }
            Spacer(Modifier.height(16.dp))
            val s = st.src
            if (s != null) {
                val fmtName = (t?.mime ?: s.mime).substringAfter('/').uppercase()
                val bits = if (s.bits > 0) " · ${s.bits}-bit" else ""
                T("$fmtName · ${"%.1f".format(s.rate / 1000f)} kHz$bits", 14, FontWeight.Normal, Metro.sub)
                T(
                    "output ${"%.1f".format(st.outRate / 1000f)} kHz" + (if (st.upsampled) " (upsampled)" else " (native)") + if (st.exclusive) " · exclusive" else "",
                    14, FontWeight.Normal, Metro.accent,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

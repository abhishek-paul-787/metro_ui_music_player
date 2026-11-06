package com.metro.music.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metro.music.audio.ArtLoader
import com.metro.music.data.Track
import kotlinx.coroutines.launch

/** Windows Phone style press: element tilts toward the touch point and shrinks slightly. */
fun Modifier.metroPress(onClick: () -> Unit, onLong: (() -> Unit)? = null): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var tilt by remember { mutableStateOf(Offset.Zero) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(90), label = "press")
    val dens = LocalDensity.current.density
    this
        .graphicsLayer {
            scaleX = scale; scaleY = scale
            rotationY = tilt.x * 10f; rotationX = -tilt.y * 10f
            cameraDistance = 12f * dens
        }
        .pointerInput(onClick, onLong) {
            detectTapGestures(
                onPress = { off ->
                    tilt = Offset((off.x / size.width - 0.5f) * 2, (off.y / size.height - 0.5f) * 2)
                    pressed = true
                    tryAwaitRelease()
                    pressed = false; tilt = Offset.Zero
                },
                onTap = { onClick() },
                onLongPress = onLong?.let { l -> { l() } },
            )
        }
}

@Composable
fun rememberArt(track: Track?, px: Int = 512): State<Bitmap?> {
    val ctx = LocalContext.current
    return produceState<Bitmap?>(null, track?.albumId, track?.id) {
        value = track?.let { ArtLoader.load(ctx, it, px) }
    }
}

@Composable
fun Art(track: Track?, modifier: Modifier = Modifier, px: Int = 512) {
    val bm by rememberArt(track, px)
    Box(modifier.background(Metro.accent)) {
        val b = bm
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MetroIcon(Ico.Note, 40.dp, Color.White.copy(alpha = 0.85f)) }
    }
}

/** Pivot control: big light lowercase headers that slide with the pager, like Windows Phone. */
@Composable
fun Pivot(titles: List<String>, state: PagerState, content: @Composable (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val widths = remember { mutableStateListOf<Int>().apply { repeat(titles.size) { add(1) } } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().clipToBounds().padding(start = 20.dp)) {
            Row(Modifier.graphicsLayer {
                val p = state.currentPage; val f = state.currentPageOffsetFraction
                var off = 0f
                for (i in 0 until p) off += widths[i]
                val cur = widths[p].toFloat()
                translationX = -(off + f * cur)
            }) {
                titles.forEachIndexed { i, t ->
                    val sel = state.currentPage == i
                    BasicText(
                        t,
                        Modifier.onSizeChanged { widths[i] = it.width + 0 }
                            .padding(end = 20.dp)
                            .graphicsLayer { alpha = if (sel) 1f else 0.4f }
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                scope.launch { state.animateScrollToPage(i) }
                            },
                        style = metroText(48.sp, FontWeight.Light, Metro.fg),
                    )
                }
            }
        }
        HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { content(it) }
    }
}

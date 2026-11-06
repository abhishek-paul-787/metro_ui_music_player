package com.metro.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.metro.music.audio.Player
import com.metro.music.data.Album
import com.metro.music.data.Artist
import com.metro.music.data.Library
import com.metro.music.data.Track

@Composable
fun T(text: String, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Metro.fg, modifier: Modifier = Modifier, lines: Int = 1) =
    BasicText(text, modifier, style = metroText(size.sp, weight, color), maxLines = lines, overflow = TextOverflow.Ellipsis)

fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun AppBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (com.metro.music.data.AppSettings.dark) Color(0xFF111111) else Color(0xFFEDEDED))
            .navigationBarsPadding().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

@Composable
fun SongRow(t: Track, onClick: () -> Unit, onLong: () -> Unit) {
    val cur by Player.current.collectAsState()
    Column(Modifier.fillMaxWidth().metroPress(onClick, onLong).padding(horizontal = 20.dp, vertical = 8.dp)) {
        T(t.title, 22, FontWeight.Normal, if (cur?.id == t.id) Metro.accent else Metro.fg)
        T("${t.artist} · ${t.album}", 14, FontWeight.Normal, Metro.sub)
    }
}

@Composable
fun QueueMenu(t: Track, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().background(Metro.tile).padding(24.dp)) {
            T(t.title, 22, FontWeight.Light, Metro.fg)
            Spacer(Modifier.height(16.dp))
            T("add to queue", 20, FontWeight.Normal, Metro.accent, Modifier.fillMaxWidth().clickable { Player.addToQueue(t); onDismiss() }.padding(vertical = 10.dp))
            T("play now", 20, FontWeight.Normal, Metro.fg, Modifier.fillMaxWidth().clickable { Player.play(listOf(t), 0); onDismiss() }.padding(vertical = 10.dp))
        }
    }
}

@Composable
fun LibraryScreen(
    pager: PagerState, songsState: LazyListState, albumsState: LazyGridState, artistsState: LazyListState,
    onAlbum: (Album) -> Unit, onArtist: (Artist) -> Unit, onNowPlaying: () -> Unit, onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf<Track?>(null) }
    val cur by Player.current.collectAsState()
    val playing by Player.isPlaying.collectAsState()
    Column(Modifier.fillMaxSize().background(Metro.bg).statusBarsPadding()) {
        T("METRO MUSIC", 14, FontWeight.SemiBold, Metro.fg, Modifier.padding(start = 22.dp, top = 14.dp, bottom = 0.dp))
        Box(Modifier.weight(1f)) {
            Pivot(listOf("songs", "albums", "artists"), pager) { page ->
                if (!Library.loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("loading…", 24, FontWeight.Light, Metro.sub) }
                else when (page) {
                    0 -> LazyColumn(Modifier.fillMaxSize(), songsState, contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(Library.songs, key = { it.id }) { t ->
                            SongRow(t, { Player.play(Library.songs, Library.songs.indexOf(t)) }, { menu = t })
                        }
                    }
                    1 -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 14.dp), albumsState,
                        contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Library.albums, key = { it.id }) { a -> AlbumTile(a) { onAlbum(a) } }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), artistsState, contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(Library.artists, key = { it.name }) { a ->
                            Column(Modifier.fillMaxWidth().metroPress({ onArtist(a) }).padding(horizontal = 20.dp, vertical = 8.dp)) {
                                T(a.name, 30, FontWeight.Light)
                                T("${a.tracks.size} songs", 14, FontWeight.Normal, Metro.sub)
                            }
                        }
                    }
                }
            }
        }
        if (cur != null) MiniPlayer(onNowPlaying)
        AppBar {
            CircleButton(Ico.Shuffle, { if (Library.songs.isNotEmpty()) Player.play(Library.songs, (Library.songs.indices).random(), true) })
            CircleButton(Ico.Note, onNowPlaying)
            CircleButton(Ico.Settings, onSettings)
        }
    }
    menu?.let { QueueMenu(it) { menu = null } }
}

@Composable
fun AlbumTile(a: Album, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).metroPress(onClick)) {
        Art(a.tracks.first(), Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(10.dp),
        ) {
            T(a.title, 16, FontWeight.SemiBold, Color.White)
            T(a.artist, 13, FontWeight.Normal, Color(0xFFDDDDDD))
        }
    }
}

@Composable
fun MiniPlayer(onClick: () -> Unit) {
    val t by Player.current.collectAsState()
    val playing by Player.isPlaying.collectAsState()
    Row(
        Modifier.fillMaxWidth().background(Metro.tile).metroPress(onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(t, Modifier.size(56.dp), 128)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(t?.title ?: "", 18, FontWeight.Normal); T(t?.artist ?: "", 14, FontWeight.Normal, Metro.sub)
        }
        CircleButton(if (playing) Ico.Pause else Ico.Play, { Player.toggle() }, 44.dp)
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
fun DetailScreen(title: String, subtitle: String, tracks: List<Track>) {
    var menu by remember { mutableStateOf<Track?>(null) }
    Column(Modifier.fillMaxSize().background(Metro.bg).statusBarsPadding()) {
        T("METRO MUSIC", 14, FontWeight.SemiBold, Metro.fg, Modifier.padding(start = 22.dp, top = 14.dp))
        T(title, 40, FontWeight.Light, Metro.fg, Modifier.padding(horizontal = 20.dp))
        T(subtitle, 16, FontWeight.Normal, Metro.accent, Modifier.padding(horizontal = 22.dp, vertical = 2.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(tracks, key = { it.id }) { t -> SongRow(t, { Player.play(tracks, tracks.indexOf(t)) }, { menu = t }) }
        }
        AppBar {
            CircleButton(Ico.Play, { Player.play(tracks, 0, false) })
            CircleButton(Ico.Shuffle, { Player.play(tracks, tracks.indices.random(), true) })
        }
    }
    menu?.let { QueueMenu(it) { menu = null } }
}

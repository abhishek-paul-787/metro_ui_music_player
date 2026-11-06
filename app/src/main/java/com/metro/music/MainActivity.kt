package com.metro.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.metro.music.data.Album
import com.metro.music.data.Artist
import com.metro.music.data.Library
import com.metro.music.ui.*
import kotlinx.coroutines.launch

sealed interface Screen {
    object Home : Screen; object Playing : Screen; object Settings : Screen
    data class AlbumS(val a: Album) : Screen
    data class ArtistS(val a: Artist) : Screen
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val perms = buildList {
                add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            val scope = rememberCoroutineScope()
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                scope.launch { Library.load(ctx) }
            }
            LaunchedEffect(Unit) { launcher.launch(perms) }

            var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
            val pager = rememberPagerState { 3 }
            val songs = remember { LazyListState() }; val albums = remember { LazyGridState() }; val artists = remember { LazyListState() }
            BackHandler(stack.size > 1) { stack = stack.dropLast(1) }

            Box(Modifier.fillMaxSize().background(Metro.bg)) {
                AnimatedContent(
                    stack.last(),
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(200)) },
                    label = "nav",
                ) { screen ->
                    val rot by transition.animateFloat(transitionSpec = { tween(280) }, label = "rot") {
                        when (it) { EnterExitState.PreEnter -> -55f; EnterExitState.Visible -> 0f; EnterExitState.PostExit -> 55f }
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        rotationY = rot; transformOrigin = TransformOrigin(0f, 0.5f); cameraDistance = 16f * density
                    }) {
                        when (screen) {
                            Screen.Home -> LibraryScreen(pager, songs, albums, artists,
                                onAlbum = { stack = stack + Screen.AlbumS(it) }, onArtist = { stack = stack + Screen.ArtistS(it) },
                                onNowPlaying = { stack = stack + Screen.Playing }, onSettings = { stack = stack + Screen.Settings })
                            Screen.Playing -> NowPlayingScreen()
                            Screen.Settings -> SettingsScreen()
                            is Screen.AlbumS -> DetailScreen(screen.a.title, screen.a.artist, screen.a.tracks)
                            is Screen.ArtistS -> DetailScreen(screen.a.name, "${screen.a.tracks.size} songs", screen.a.tracks)
                        }
                    }
                }
            }
        }
    }
}


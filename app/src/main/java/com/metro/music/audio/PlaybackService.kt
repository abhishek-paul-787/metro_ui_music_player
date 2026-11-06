package com.metro.music.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.metro.music.MainActivity
import com.metro.music.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class PlaybackService : Service() {
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var art: Bitmap? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Playback", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "MetroMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Player.resume()
                override fun onPause() = Player.pause()
                override fun onSkipToNext() = Player.next()
                override fun onSkipToPrevious() = Player.prev()
                override fun onSeekTo(pos: Long) = Player.seekTo(pos)
            })
            isActive = true
        }
        startForeground(1, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        scope.launch {
            combine(Player.current, Player.isPlaying) { t, p -> t to p }.collect { (t, p) ->
                art = t?.let { ArtLoader.load(this@PlaybackService, it) }
                refresh(p)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            A_TOGGLE -> Player.toggle()
            A_NEXT -> Player.next()
            A_PREV -> Player.prev()
        }
        return START_NOT_STICKY
    }

    private fun refresh(playing: Boolean) {
        val t = Player.current.value
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, t?.title ?: "")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, t?.artist ?: "")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, t?.album ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, t?.durationMs ?: 0)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build())
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, Player.positionMs.value, if (playing) 1f else 0f)
            .build())
        getSystemService(NotificationManager::class.java).notify(1, build())
    }

    private fun act(a: String, rc: Int) = PendingIntent.getService(this, rc,
        Intent(this, PlaybackService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)

    private fun build(): Notification {
        val t = Player.current.value
        val playing = Player.isPlaying.value
        fun action(res: Int, label: String, a: String, rc: Int) =
            Notification.Action.Builder(Icon.createWithResource(this, res), label, act(a, rc)).build()
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t?.title ?: "Metro Music").setContentText(t?.artist ?: "")
            .setLargeIcon(art)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(action(R.drawable.ic_prev, "Previous", A_PREV, 1))
            .addAction(action(if (playing) R.drawable.ic_pause else R.drawable.ic_play, "Play/Pause", A_TOGGLE, 2))
            .addAction(action(R.drawable.ic_next, "Next", A_NEXT, 3))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() { scope.cancel(); session.release(); super.onDestroy() }

    companion object {
        const val CH = "playback"
        const val A_TOGGLE = "metro.TOGGLE"; const val A_NEXT = "metro.NEXT"; const val A_PREV = "metro.PREV"
    }
}

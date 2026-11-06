package com.metro.music

import android.app.Application
import com.metro.music.audio.Player
import com.metro.music.data.AppSettings

class MetroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Player.init(this)
    }
}

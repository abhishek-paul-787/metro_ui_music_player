package com.metro.music.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Library {
    var songs by mutableStateOf<List<Track>>(emptyList()); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var artists by mutableStateOf<List<Artist>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
        val out = ArrayList<Track>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
                out.add(Track(
                    id, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    c.getString(1) ?: "Unknown", artist, c.getString(3) ?: "Unknown album", c.getLong(4),
                    c.getLong(5), c.getInt(6) % 1000, c.getString(7) ?: "", c.getLong(8), c.getInt(9),
                ))
            }
        }
        val al = out.groupBy { it.albumId }.map { (id, t) ->
            val s = t.sortedWith(compareBy({ it.trackNo }, { it.title }))
            Album(id, s[0].album, s[0].artist, s)
        }.sortedBy { it.title.lowercase() }
        val ar = out.groupBy { it.artist }.map { (n, t) -> Artist(n, t) }.sortedBy { it.name.lowercase() }
        withContext(Dispatchers.Main) { songs = out; albums = al; artists = ar; loaded = true }
    }
}

package com.metro.music.audio

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import com.metro.music.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtLoader {
    private val cache = LruCache<Long, Bitmap>(64)
    private val missing = HashSet<Long>()

    suspend fun load(ctx: Context, t: Track, px: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(t.albumId)?.let { return@withContext it }
        if (synchronized(missing) { t.albumId in missing }) return@withContext null
        val bm = try { ctx.contentResolver.loadThumbnail(t.uri, Size(px, px), null) } catch (_: Exception) { null }
        if (bm != null) cache.put(t.albumId, bm) else synchronized(missing) { missing.add(t.albumId) }
        bm
    }
}

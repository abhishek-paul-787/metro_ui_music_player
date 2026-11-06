package com.metro.music.audio

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.metro.music.data.AppSettings
import com.metro.music.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import kotlin.math.max

data class StreamStatus(
    val src: SrcFormat? = null, val outRate: Int = 0, val exclusive: Boolean = false,
    val burst: Int = 0, val upsampled: Boolean = false,
)

object Player {
    private const val TAG = "MetroPlayer"
    private lateinit var app: Context
    private val engine = NativeEngine

    val queue = MutableStateFlow<List<Track>>(emptyList())
    val current = MutableStateFlow<Track?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val shuffle = MutableStateFlow(false)
    val repeat = MutableStateFlow(0) // 0 off, 1 all, 2 one
    val status = MutableStateFlow(StreamStatus())

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var order = IntArray(0)
    @Volatile private var curIdx = -1
    @Volatile private var wantPlay = false
    private var opened = false
    private var reqRate = 0
    private var reqExcl = true
    @Volatile private var ending = false

    private class Job { @Volatile var cancelled = false; @Volatile var finished = false; var thread: Thread? = null }
    private class Boundary(val startFrame: Long, val index: Int, val baseMs: Long, val rate: Int)
    @Volatile private var job: Job? = null
    private val boundaries = ArrayDeque<Boundary>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        applyEq()
        main.post(object : Runnable {
            override fun run() { tick(); main.postDelayed(this, 250) }
        })
    }

    // ---- public controls ----
    fun play(tracks: List<Track>, start: Int, shuffleOn: Boolean = shuffle.value) {
        exec.execute {
            queue.value = tracks
            shuffle.value = shuffleOn
            rebuildOrder(start)
            startService()
            startAt(start, 0, true)
        }
    }
    fun addToQueue(t: Track) = exec.execute {
        if (queue.value.isEmpty()) { play(listOf(t), 0); return@execute }
        queue.value = queue.value + t
        order = order + (queue.value.size - 1)
    }
    fun toggle() = exec.execute {
        if (current.value == null) return@execute
        if (isPlaying.value) { wantPlay = false; isPlaying.value = false; engine.nPause() }
        else {
            wantPlay = true; isPlaying.value = true; startService()
            val j = job
            if (j == null || (j.finished && engine.nBuffered() == 0)) startAt(curIdx, positionMs.value, true) else engine.nStart()
        }
    }
    fun resume() { if (!isPlaying.value) toggle() }
    fun pause() { if (isPlaying.value) toggle() }
    fun next() = exec.execute { if (queue.value.isNotEmpty()) startAt(step(curIdx, 1), 0, true) }
    fun prev() = exec.execute {
        if (queue.value.isEmpty()) return@execute
        if (positionMs.value > 3000) startAt(curIdx, 0, wantPlay) else startAt(step(curIdx, -1), 0, wantPlay)
    }
    fun seekTo(ms: Long) = exec.execute { if (curIdx >= 0) startAt(curIdx, ms, wantPlay) }
    fun playIndex(i: Int) = exec.execute { startAt(i, 0, true); wantPlay = true }
    fun toggleShuffle() = exec.execute { shuffle.value = !shuffle.value; rebuildOrder(curIdx) }
    fun cycleRepeat() { repeat.value = (repeat.value + 1) % 3 }

    /** Call after changing output settings (rate cap / exclusive). Restarts current track. */
    fun reapplyOutput() = exec.execute { if (curIdx >= 0) startAt(curIdx, positionMs.value, wantPlay) }
    fun applyEq() {
        engine.nSetEq(AppSettings.eqGains.toFloatArray(), AppSettings.preamp, AppSettings.eqOn)
    }

    // ---- internals (exec thread) ----
    private fun startService() = main.post {
        try { ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java)) } catch (e: Exception) { Log.w(TAG, e) }
    }

    private fun rebuildOrder(keep: Int) {
        val n = queue.value.size
        val idx = (0 until n).toMutableList()
        if (shuffle.value) { idx.shuffle(); if (keep in 0 until n) { idx.remove(keep); idx.add(0, keep) } }
        order = idx.toIntArray()
    }

    /** Next index in play order. wrap=true always wraps; wrap=false returns -1 at the end. */
    private fun step(from: Int, dir: Int): Int {
        val p = order.indexOf(from)
        return order[((p + dir) % order.size + order.size) % order.size]
    }
    private fun autoNext(from: Int): Int {
        if (repeat.value == 2) return from
        val p = order.indexOf(from)
        return if (p + 1 < order.size) order[p + 1] else if (repeat.value == 1) order[0] else -1
    }

    private fun stopJob() {
        val j = job ?: return
        j.cancelled = true
        engine.nAbort(true)
        j.thread?.join()
        engine.nAbort(false)
        job = null
    }

    private fun startAt(i: Int, ms: Long, play: Boolean) {
        stopJob()
        engine.nFlush()
        synchronized(boundaries) { boundaries.clear() }
        val q = queue.value
        if (i !in q.indices) return
        ending = false
        curIdx = i; current.value = q[i]; positionMs.value = ms
        wantPlay = play; isPlaying.value = play
        val j = Job(); job = j
        j.thread = Thread { runJob(j, i, ms) }.apply { name = "metro-decode"; start() }
        if (play && opened) engine.nStart()
    }

    private fun runJob(j: Job, startIdx: Int, startMs: Long) {
        var idx = startIdx; var seek = startMs
        while (!j.cancelled) {
            val t = queue.value.getOrNull(idx) ?: break
            try {
                var boundaryAdded = false
                AudioDecoder(app, t.uri).run(seek, { j.cancelled },
                    onFormat = { f ->
                        prepareOutput(f, j)
                        if (!boundaryAdded) {
                            boundaryAdded = true
                            val rate = engine.nStreamInfo()[0]
                            synchronized(boundaries) { boundaries.addLast(Boundary(engine.nWritten(), idx, seek, max(rate, 1))) }
                        }
                    },
                    onData = { buf, off, bytes, f -> engine.nWrite(buf, off, bytes, f.rate, f.channels, f.enc) && !j.cancelled },
                )
            } catch (e: Exception) { Log.e(TAG, "decode failed: ${t.title}", e) }
            if (j.cancelled) break
            seek = 0
            val n = autoNext(idx)
            if (n < 0) break
            idx = n
        }
        j.finished = true
    }

    private fun desiredRate(src: Int): Int {
        val cap = AppSettings.rateCap
        if (cap <= 0 || cap <= src) return src
        val base = if (src % 44100 == 0) 44100 else 48000
        var best = src
        for (k in intArrayOf(1, 2, 4, 8)) if (base * k <= cap && base * k > best) best = base * k
        return best
    }

    private fun prepareOutput(f: SrcFormat, j: Job) {
        val desired = desiredRate(f.rate)
        val excl = AppSettings.exclusive
        if (!opened || desired != reqRate || excl != reqExcl) {
            if (opened && isPlaying.value) {
                while (engine.nBuffered() > 0 && !j.cancelled) Thread.sleep(10)
            }
            if (j.cancelled) return
            engine.nClose()
            val actual = engine.nOpen(desired, excl)
            opened = actual > 0; reqRate = desired; reqExcl = excl
            if (opened && wantPlay) engine.nStart()
        }
        val si = engine.nStreamInfo()
        status.value = StreamStatus(f, si[0], si[1] == 1, si[2], si[0] != f.rate)
    }

    // ---- main thread ticker ----
    private fun tick() {
        if (curIdx < 0) return
        val played = engine.nPlayed()
        var changed: Track? = null
        synchronized(boundaries) {
            while (boundaries.size >= 2 && played >= boundaries[1].startFrame) boundaries.removeFirst()
            val b = boundaries.firstOrNull() ?: return
            if (b.index != curIdx) { curIdx = b.index; changed = queue.value.getOrNull(b.index) }
            positionMs.value = b.baseMs + max(0L, played - b.startFrame) * 1000 / b.rate
            val j = job
            if (j != null && j.finished && wantPlay && !ending && boundaries.size <= 1 && engine.nBuffered() == 0 && played > 0) {
                ending = true
                exec.execute { startAt(order.firstOrNull() ?: 0, 0, false) }
            }
        }
        changed?.let { current.value = it }
    }
}

package com.metro.music.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer

data class SrcFormat(val rate: Int, val channels: Int, val enc: Int, val bits: Int, val mime: String, val bitrate: Int)

/** Decodes any Android-supported audio file to PCM (float when the codec allows it). */
class AudioDecoder(private val ctx: Context, private val uri: Uri) {
    private var scratch: ByteBuffer? = null

    /** Returns true when the end of the file was reached, false when cancelled. */
    fun run(
        startMs: Long, cancelled: () -> Boolean,
        onFormat: (SrcFormat) -> Unit, onData: (ByteBuffer, Int, Int, SrcFormat) -> Boolean,
    ): Boolean {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var ti = -1
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { ti = i; break }
        }
        if (ti < 0) { ex.release(); throw IllegalStateException("no audio track") }
        ex.selectTrack(ti)
        val inFmt = ex.getTrackFormat(ti)
        val mime = inFmt.getString(MediaFormat.KEY_MIME)!!
        val bits = if (inFmt.containsKey("bits-per-sample")) inFmt.getInteger("bits-per-sample") else -1
        val br = if (inFmt.containsKey(MediaFormat.KEY_BIT_RATE)) inFmt.getInteger(MediaFormat.KEY_BIT_RATE) else -1
        val codec = MediaCodec.createDecoderByType(mime)
        inFmt.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
        codec.configure(inFmt, null, null, 0)
        codec.start()
        if (startMs > 0) ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        var fmt = SrcFormat(inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT), AudioFormat.ENCODING_PCM_16BIT, bits, mime, br)
        var fmtSent = false
        try {
            while (!outDone && !cancelled()) {
                if (!inDone) {
                    val ib = codec.dequeueInputBuffer(5000)
                    if (ib >= 0) {
                        val b = codec.getInputBuffer(ib)!!
                        val n = ex.readSampleData(b, 0)
                        if (n < 0) { codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ib, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val ob = codec.dequeueOutputBuffer(info, 5000)
                if (ob >= 0) {
                    if (!fmtSent) { onFormat(fmt); fmtSent = true }
                    if (info.size > 0) {
                        val out = codec.getOutputBuffer(ob)!!
                        var buf = out; var off = info.offset
                        if (!out.isDirect) {
                            if (scratch == null || scratch!!.capacity() < info.size) scratch = ByteBuffer.allocateDirect(info.size * 2)
                            scratch!!.clear(); out.position(info.offset); out.limit(info.offset + info.size)
                            scratch!!.put(out); buf = scratch!!; off = 0
                        }
                        val ok = onData(buf, off, info.size, fmt)
                        if (!ok) { codec.releaseOutputBuffer(ob, false); return false }
                    }
                    codec.releaseOutputBuffer(ob, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    val enc = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    fmt = fmt.copy(rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT), enc = enc)
                    if (fmtSent) onFormat(fmt)
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release(); ex.release()
        }
        return outDone
    }
}

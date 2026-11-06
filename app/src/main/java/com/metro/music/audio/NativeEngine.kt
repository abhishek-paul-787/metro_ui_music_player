package com.metro.music.audio

import java.nio.ByteBuffer

object NativeEngine {
    init { System.loadLibrary("metroengine") }
    external fun nOpen(rate: Int, exclusive: Boolean): Int
    external fun nClose()
    external fun nStart(): Boolean
    external fun nPause()
    external fun nFlush()
    external fun nAbort(abort: Boolean)
    external fun nWrite(buf: ByteBuffer, offset: Int, bytes: Int, inRate: Int, channels: Int, enc: Int): Boolean
    external fun nBuffered(): Int
    external fun nPlayed(): Long
    external fun nWritten(): Long
    external fun nSetEq(gains: FloatArray, preampDb: Float, enabled: Boolean)
    external fun nStreamInfo(): IntArray
}

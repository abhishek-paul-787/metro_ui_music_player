package com.metro.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

object AppSettings {
    private lateinit var sp: SharedPreferences
    private val _accent = mutableIntStateOf(0xFF1BA1E2.toInt())
    private val _dark = mutableStateOf(true)
    private val _rateCap = mutableIntStateOf(0)
    private val _exclusive = mutableStateOf(true)
    private val _eqOn = mutableStateOf(false)
    private val _preamp = mutableFloatStateOf(0f)
    val eqGains = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("metro", Context.MODE_PRIVATE)
        _accent.intValue = sp.getInt("accent", _accent.intValue)
        _dark.value = sp.getBoolean("dark", true)
        _rateCap.intValue = sp.getInt("rateCap", 0)
        _exclusive.value = sp.getBoolean("excl", true)
        _eqOn.value = sp.getBoolean("eqOn", false)
        _preamp.floatValue = sp.getFloat("preamp", 0f)
        for (i in 0 until 10) eqGains[i] = sp.getFloat("eq$i", 0f)
    }

    var accent: Int get() = _accent.intValue
        set(v) { _accent.intValue = v; sp.edit().putInt("accent", v).apply() }
    var dark: Boolean get() = _dark.value
        set(v) { _dark.value = v; sp.edit().putBoolean("dark", v).apply() }
    /** 0 = native (no upsampling), otherwise max output rate in Hz. */
    var rateCap: Int get() = _rateCap.intValue
        set(v) { _rateCap.intValue = v; sp.edit().putInt("rateCap", v).apply() }
    var exclusive: Boolean get() = _exclusive.value
        set(v) { _exclusive.value = v; sp.edit().putBoolean("excl", v).apply() }
    var eqOn: Boolean get() = _eqOn.value
        set(v) { _eqOn.value = v; sp.edit().putBoolean("eqOn", v).apply() }
    var preamp: Float get() = _preamp.floatValue
        set(v) { _preamp.floatValue = v; sp.edit().putFloat("preamp", v).apply() }
    fun setGain(i: Int, v: Float) { eqGains[i] = v; sp.edit().putFloat("eq$i", v).apply() }
}

package com.example.wordexplainer

import android.content.Context

class Prefs(c: Context) {
    private val p = c.getSharedPreferences("word_explainer", Context.MODE_PRIVATE)

    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v).apply()

    var key: String
        get() = p.getString("key", "") ?: ""
        set(v) = p.edit().putString("key", v).apply()

    // "right" or "left" — which screen edge the bubble rests on (default: right)
    var circleSide: String
        get() = p.getString("circle_side", "right") ?: "right"
        set(v) = p.edit().putString("circle_side", v).apply()

    // Exact saved X position of circle (used after interactive drag-to-set)
    var circleHomeX: Int
        get() = p.getInt("circle_home_x", -1)   // -1 = auto (use side/percent)
        set(v) = p.edit().putInt("circle_home_x", v).apply()

    // Exact saved Y position of circle
    var circleHomeY: Int
        get() = p.getInt("circle_home_y", -1)    // -1 = auto
        set(v) = p.edit().putInt("circle_home_y", v).apply()

    // Bubble size in dp — freely adjustable via slider (range 36–80, default 54)
    var circleSizeDp: Int
        get() = p.getInt("circle_size_dp", 54)
        set(v) = p.edit().putInt("circle_size_dp", v.coerceIn(36, 80)).apply()

    // Bubble opacity in percent (range 20–100, default 80)
    var circleAlpha: Int
        get() = p.getInt("circle_alpha", 80)
        set(v) = p.edit().putInt("circle_alpha", v.coerceIn(20, 100)).apply()

    // Bubble edge peek in dp — protrusion visible from bezel edge (range 14–45, default 27)
    var circlePeekDp: Int
        get() = p.getInt("circle_peek_dp", 27)
        set(v) = p.edit().putInt("circle_peek_dp", v.coerceIn(14, 45)).apply()
}


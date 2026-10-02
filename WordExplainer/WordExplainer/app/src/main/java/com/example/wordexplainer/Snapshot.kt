package com.example.wordexplainer

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.*

data class Word(val text: String, val rect: Rect, val block: Int)
class Block(val text: String, val rect: Rect, val words: List<Word>)
private class Raw(val text: String, val rect: Rect, val chars: Array<RectF?>?)

/** A saved copy of the screen's text and where each word sits. Read once per drag. */
class Snapshot(val blocks: List<Block>) {
    val words: List<Word> = blocks.flatMap { it.words }

    /**
     * Word at (x,y). Y-distance counts 2× so the crosshair snaps to the correct
     * LINE first, then finds the nearest word on that line.
     * Effective snap radius ≈ 140px.
     */
    fun wordAt(x: Int, y: Int): Word? {
        var best: Word? = null
        var bestD = Float.MAX_VALUE
        for (w in words) {
            val dx = max(max(w.rect.left - x, x - w.rect.right), 0)
            val dy = max(max(w.rect.top - y, y - w.rect.bottom), 0)
            // Y counts 2× — snaps to correct line before caring about X distance
            val d = hypot(dx.toFloat(), dy.toFloat() * 2f)
            if (d < bestD) { bestD = d; best = w }
        }
        // 140px * 2 (Y-weight) → threshold 280f in weighted metric
        if (bestD <= 280f) return best

        // If no word within threshold, check if (x,y) is inside any block
        val bi = blockAt(x, y)
        if (bi != null && blocks[bi].words.isNotEmpty()) {
            return blocks[bi].words.minByOrNull { w ->
                val dx = max(max(w.rect.left - x, x - w.rect.right), 0)
                val dy = max(max(w.rect.top - y, y - w.rect.bottom), 0)
                hypot(dx.toFloat(), dy.toFloat() * 2f)
            }
        }
        return null
    }

    /** Smallest block containing (x,y), or nearest block within 150px. */
    fun blockAt(x: Int, y: Int): Int? {
        val containing = blocks.indices.filter { blocks[it].rect.contains(x, y) }
        if (containing.isNotEmpty()) {
            return containing.minByOrNull { blocks[it].rect.width().toLong() * blocks[it].rect.height() }
        }
        var bestIdx: Int? = null
        var bestD = Float.MAX_VALUE
        for (i in blocks.indices) {
            val r = blocks[i].rect
            val dx = max(max(r.left - x, x - r.right), 0)
            val dy = max(max(r.top - y, y - r.bottom), 0)
            val d = hypot(dx.toFloat(), dy.toFloat())
            if (d < bestD) { bestD = d; bestIdx = i }
        }
        return if (bestD <= 150f) bestIdx else null
    }

    fun fullText(): String = blocks.joinToString("\n") { it.text }

    companion object {

        /**
         * A node qualifies as text if:
         *  - It has non-blank .text OR non-blank .contentDescription (fallback for React Native,
         *    WebView, X/Twitter, custom renderers that put readable text in contentDescription)
         *  - Its class is NOT a pure image type (ImageView, ImageButton — these use
         *    contentDescription for alt-text, not for readable content)
         */
        private fun isTextNode(n: AccessibilityNodeInfo): Boolean {
            val cls = n.className?.toString() ?: ""
            // Reject image/icon classes regardless of what text they carry
            if (cls.contains("ImageView", ignoreCase = true) ||
                cls.contains("ImageButton", ignoreCase = true)) return false
            // Accept if either .text or .contentDescription has readable content
            val text = n.text?.toString()
            val desc = n.contentDescription?.toString()
            return !text.isNullOrBlank() || !desc.isNullOrBlank()
        }

        fun build(root: AccessibilityNodeInfo?, ownPkg: String): Snapshot {
            val raws = ArrayList<Raw>()

            fun walk(n: AccessibilityNodeInfo?) {
                if (n == null) return
                try {
                    if (n.isPassword) return
                    if (n.packageName?.toString() == ownPkg) return

                    val r = Rect()
                    n.getBoundsInScreen(r)

                    if (isTextNode(n) && !r.isEmpty && n.isVisibleToUser) {
                        // Prefer .text; fall back to .contentDescription (React Native, WebView, X/Twitter)
                        val rawText = (n.text ?: n.contentDescription)?.toString()
                        if (!rawText.isNullOrBlank()) {
                            raws.add(Raw(rawText, r, charRects(n, rawText.length)))
                        }
                    }

                    for (i in 0 until n.childCount) {
                        try {
                            val child = n.getChild(i)
                            walk(child)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    // Node was recycled by Android between calls — skip it safely
                }
            }

            try { walk(root) } catch (_: Exception) {}

            val blocks = ArrayList<Block>()
            // Reading order: top-to-bottom, left-to-right
            raws.sortedWith(compareBy<Raw>({ it.rect.top }, { it.rect.left })).forEachIndexed { bi, r ->
                val words = Regex("\\S+").findAll(r.text)
                    .map { m -> Word(m.value, wordRect(r, m.range), bi) }
                    .toList()
                blocks.add(Block(r.text, r.rect, words))
            }
            return Snapshot(blocks)
        }

        private fun charRects(n: AccessibilityNodeInfo, len: Int): Array<RectF?>? {
            return try {
                val args = Bundle().apply {
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                    putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, min(len, 2000))
                }
                if (!n.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)) return null
                @Suppress("DEPRECATION")
                val arr = n.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
                    ?: return null
                Array(arr.size) { arr[it] as? RectF }
            } catch (e: Exception) { null }
        }

        private fun wordRect(r: Raw, range: IntRange): Rect {
            val u = RectF(); var has = false
            for (i in range) {
                val c = r.chars?.getOrNull(i) ?: continue
                if (c.isEmpty) continue
                if (!has) { u.set(c); has = true } else u.union(c)
            }
            if (has) return Rect(u.left.toInt(), u.top.toInt(), u.right.toInt(), u.bottom.toInt())

            val b = r.rect
            val lines = if (b.height() < 90) 1 else max(1, (b.height() / 60f).roundToInt())
            val cpl = max(1, ceil(r.text.length / lines.toFloat()).toInt())
            val line = min(range.first / cpl, lines - 1)
            val cw = b.width().toFloat() / cpl
            val left = b.left + (range.first % cpl) * cw
            val lh = b.height() / lines
            return Rect(
                left.toInt(),
                b.top + line * lh,
                min(b.right.toFloat(), left + (range.last - range.first + 1) * cw).toInt(),
                b.top + (line + 1) * lh
            )
        }
    }
}

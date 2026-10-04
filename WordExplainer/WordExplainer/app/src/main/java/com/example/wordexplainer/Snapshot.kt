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
     * Snaps strictly when the crosshair is on or immediately adjacent to a word.
     * Empty whitespace and margins return null so no phantom selection occurs.
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
        // Strict snap tolerance (~80f in Y-weighted metric ≈ 40px unweighted, ~14-16dp).
        // If cursor is beyond this tolerance (whitespace, margins, blank space), return null.
        if (bestD <= 80f) return best

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
        fun build(root: AccessibilityNodeInfo?, ownPkg: String): Snapshot {
            val raws = ArrayList<Raw>()

            fun walk(n: AccessibilityNodeInfo?): Boolean {
                if (n == null) return false
                try {
                    if (n.isPassword) return false
                    if (n.packageName?.toString() == ownPkg) return false

                    val r = Rect()
                    n.getBoundsInScreen(r)
                    val hasValidBounds = r.width() > 0 && r.height() > 0 && r.bottom > 0

                    var childHadText = false
                    val count = n.childCount
                    for (i in 0 until count) {
                        try {
                            val child = n.getChild(i)
                            if (walk(child)) {
                                childHadText = true
                            }
                        } catch (_: Exception) {}
                    }

                    val rawTextVal = n.text?.toString()?.trim()
                    val rawDescVal = n.contentDescription?.toString()?.trim()
                    val hasText = !rawTextVal.isNullOrBlank()
                    val hasDesc = !rawDescVal.isNullOrBlank()
                    val cls = n.className?.toString() ?: ""
                    val isImage = cls.contains("ImageView", ignoreCase = true) ||
                                  cls.contains("ImageButton", ignoreCase = true)

                    if (hasValidBounds && !isImage) {
                        if (hasText) {
                            raws.add(Raw(rawTextVal!!, r, charRects(n, rawTextVal.length)))
                            return true
                        } else if (hasDesc && !childHadText) {
                            raws.add(Raw(rawDescVal!!, r, charRects(n, rawDescVal.length)))
                            return true
                        }
                    }

                    return childHadText || hasText
                } catch (_: Exception) {
                    return false
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
            val rawText = r.text
            val lines = rawText.split('\n')
            val numLines = lines.size
            if (numLines > 1) {
                var charCount = 0
                var targetLineIdx = 0
                var charInLine = 0
                for ((idx, line) in lines.withIndex()) {
                    val lineLen = line.length + 1 // +1 for the '\n'
                    if (range.first < charCount + lineLen) {
                        targetLineIdx = idx
                        charInLine = range.first - charCount
                        break
                    }
                    charCount += lineLen
                }
                val lh = max(1, b.height() / numLines)
                val currentLineText = lines.getOrElse(targetLineIdx) { "" }
                val lineChars = max(1, currentLineText.length)
                val cw = b.width().toFloat() / lineChars
                val left = b.left + charInLine * cw
                val wordLen = range.last - range.first + 1
                val top = b.top + targetLineIdx * lh
                return Rect(
                    left.toInt(),
                    top,
                    min(b.right.toFloat(), left + wordLen * cw).toInt(),
                    top + lh
                )
            }

            val estLines = if (b.height() < 90) 1 else max(1, (b.height() / 60f).roundToInt())
            val cpl = max(1, ceil(rawText.length / estLines.toFloat()).toInt())
            val line = min(range.first / cpl, estLines - 1)
            val charInLine = range.first % cpl
            val cw = b.width().toFloat() / cpl
            val left = b.left + charInLine * cw
            val lh = max(1, b.height() / estLines)
            val wordLen = range.last - range.first + 1
            val top = b.top + line * lh
            return Rect(
                left.toInt(),
                top,
                min(b.right.toFloat(), left + wordLen * cw).toInt(),
                top + lh
            )
        }
    }
}

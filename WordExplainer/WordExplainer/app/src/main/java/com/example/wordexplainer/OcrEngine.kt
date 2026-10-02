package com.example.wordexplainer

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps Google ML Kit's bundled (offline) Latin text recognizer.
 *
 * Captures screen pixels via [AccessibilityService.takeScreenshot] (API 30+)
 * and builds a pixel-accurate [Snapshot] sorted in true top-to-bottom reading order.
 */
object OcrEngine {

    private const val TAG = "FleenchOCR"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun buildSnapshot(service: AccessibilityService): Snapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "Device SDK is below Android 11 (R). Screenshot API unavailable.")
            return null
        }

        val bitmapRef = AtomicReference<Bitmap?>(null)
        val screenLatch = CountDownLatch(1)

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hw = screenshot.hardwareBuffer
                            val cs = screenshot.colorSpace
                            val wrapped = Bitmap.wrapHardwareBuffer(hw, cs)
                            val softCopy = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                            hw.close()
                            bitmapRef.set(softCopy)
                            Log.d(TAG, "Screenshot captured successfully: ${softCopy?.width}x${softCopy?.height}")
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error wrapping screenshot hardware buffer", t)
                        }
                        screenLatch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "takeScreenshot failed with error code: $errorCode")
                        screenLatch.countDown()
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Exception calling takeScreenshot", t)
            return null
        }

        if (!screenLatch.await(2, TimeUnit.SECONDS)) {
            Log.w(TAG, "Screenshot timed out after 2 seconds.")
            return null
        }

        val bitmap = bitmapRef.get() ?: return null

        val dm = service.resources.displayMetrics
        val scaleX = dm.widthPixels.toFloat() / bitmap.width
        val scaleY = dm.heightPixels.toFloat() / bitmap.height

        return try {
            fromBitmap(bitmap, scaleX, scaleY, dm.density)
        } catch (t: Throwable) {
            Log.e(TAG, "Error processing bitmap through ML Kit", t)
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun fromBitmap(bitmap: Bitmap, scaleX: Float, scaleY: Float, density: Float): Snapshot? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val resultRef = AtomicReference<com.google.mlkit.vision.text.Text?>(null)
        val ocrLatch = CountDownLatch(1)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                resultRef.set(visionText)
                ocrLatch.countDown()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit OCR failed", e)
                ocrLatch.countDown()
            }

        if (!ocrLatch.await(3, TimeUnit.SECONDS)) {
            Log.w(TAG, "ML Kit OCR recognition timed out.")
            return null
        }

        val visionText = resultRef.get() ?: return null

        // Exclude system status bar (top 42dp) and navigation gesture bar (bottom 48dp)
        val statusBarPx = (42 * density).toInt()
        val navBarPx = (48 * density).toInt()
        val screenHeightPx = (bitmap.height * scaleY).toInt()

        // 1. Sort blocks in physical screen reading order (Top-to-Bottom, Left-to-Right)
        val sortedBlocks = visionText.textBlocks
            .filter { tb ->
                val box = tb.boundingBox?.scaled(scaleX, scaleY) ?: return@filter false
                // Skip status bar at top and navigation bar at bottom
                box.bottom > statusBarPx && box.top < (screenHeightPx - navBarPx)
            }
            .sortedWith(
                compareBy(
                    // Group blocks whose tops are within ~14dp into the same line band
                    { (it.boundingBox?.top ?: 0) / maxOf(1, (14 * density).toInt()) },
                    { it.boundingBox?.left ?: 0 }
                )
            )

        val blocks = ArrayList<Block>()
        var blockIdx = 0

        for (tb in sortedBlocks) {
            val blockBounds = tb.boundingBox?.scaled(scaleX, scaleY) ?: continue
            val wordList = ArrayList<Word>()

            for (line in tb.lines) {
                for (element in line.elements) {
                    val wBounds = element.boundingBox?.scaled(scaleX, scaleY) ?: continue
                    val wText = element.text.trim()
                    if (wText.isNotBlank()) {
                        wordList.add(Word(wText, wBounds, blockIdx))
                    }
                }
            }

            if (wordList.isEmpty()) continue
            val blockText = tb.lines.joinToString("\n") { it.text }
            blocks.add(Block(blockText, blockBounds, wordList))
            blockIdx++
        }

        Log.d(TAG, "ML Kit produced ${blocks.size} geometrically sorted blocks with ${blocks.sumOf { it.words.size }} words.")
        return if (blocks.isEmpty()) null else Snapshot(blocks)
    }

    private fun Rect.scaled(sx: Float, sy: Float): Rect = Rect(
        (left   * sx).toInt(),
        (top    * sy).toInt(),
        (right  * sx).toInt(),
        (bottom * sy).toInt()
    )
}

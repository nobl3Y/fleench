package com.example.wordexplainer

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
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
 * On API 30+ it captures the screen via [AccessibilityService.takeScreenshot] and builds
 * a pixel-accurate [Snapshot] from the result so every word's on-screen bounding box
 * comes directly from the rendered pixels, not from the accessibility tree.
 *
 * On older API levels it returns null so the caller can fall back to the
 * [Snapshot.build] / AccessibilityNodeInfo path.
 */
object OcrEngine {

    // Lazily initialized once — reused for every snapshot to avoid cold-start overhead.
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Returns a [Snapshot] built from on-device OCR, or null if:
     * - Device is below API 30 (takeScreenshot unavailable).
     * - Screenshot fails for any reason.
     * - ML Kit processing fails or times out.
     *
     * Must NOT be called on the main/UI thread — it blocks until ML Kit finishes.
     */
    fun buildSnapshot(service: AccessibilityService): Snapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val bitmapRef  = AtomicReference<Bitmap?>(null)
        val screenLatch = CountDownLatch(1)

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hw = screenshot.hardwareBuffer
                            // Convert the HardwareBuffer to a software Bitmap for ML Kit.
                            bitmapRef.set(
                                Bitmap.wrapHardwareBuffer(hw, null)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                            )
                            hw.close()
                        } catch (_: Throwable) {}
                        screenLatch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        screenLatch.countDown()
                    }
                }
            )
        } catch (_: Throwable) {
            return null
        }

        if (!screenLatch.await(2, TimeUnit.SECONDS)) return null
        val bitmap = bitmapRef.get() ?: return null

        // The HardwareBuffer can theoretically differ from screen px if the device
        // reports a different logical vs physical resolution. Scale to be safe.
        val dm      = service.resources.displayMetrics
        val scaleX  = dm.widthPixels.toFloat()  / bitmap.width
        val scaleY  = dm.heightPixels.toFloat() / bitmap.height

        return try {
            fromBitmap(bitmap, scaleX, scaleY)
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    // ── ML Kit processing ─────────────────────────────────────────────────────

    private fun fromBitmap(bitmap: Bitmap, scaleX: Float, scaleY: Float): Snapshot? {
        val image      = InputImage.fromBitmap(bitmap, 0)
        val resultRef  = AtomicReference<com.google.mlkit.vision.text.Text?>(null)
        val ocrLatch   = CountDownLatch(1)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                resultRef.set(visionText)
                ocrLatch.countDown()
            }
            .addOnFailureListener {
                ocrLatch.countDown()
            }

        if (!ocrLatch.await(3, TimeUnit.SECONDS)) return null
        val visionText = resultRef.get() ?: return null

        val blocks = ArrayList<Block>()
        var blockIdx = 0

        for (tb in visionText.textBlocks) {
            val blockBounds = tb.boundingBox?.scaled(scaleX, scaleY) ?: continue
            val wordList    = ArrayList<Word>()

            for (line in tb.lines) {
                for (element in line.elements) {
                    val wBounds = element.boundingBox?.scaled(scaleX, scaleY) ?: continue
                    val wText   = element.text
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

        return if (blocks.isEmpty()) null else Snapshot(blocks)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Rect.scaled(sx: Float, sy: Float): Rect = Rect(
        (left   * sx).toInt(),
        (top    * sy).toInt(),
        (right  * sx).toInt(),
        (bottom * sy).toInt()
    )
}

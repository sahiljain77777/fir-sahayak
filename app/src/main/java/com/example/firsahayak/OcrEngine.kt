package com.example.firsahayak

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * OcrEngine — ML Kit OCR with spatial sorting.
 *
 * WHY THIS MATTERS FOR FIR FORMS:
 * ─────────────────────────────────
 * The standard BNS FIR form is a multi-column, table-heavy layout.
 * ML Kit returns text blocks in bounding-box order which does NOT match
 * reading order for forms. Fields like "District | Year | P.S." on the
 * same row get interleaved with content from other rows.
 *
 * FIX — Two-pass spatial sort per page:
 *   Pass 1: Group all text blocks into horizontal "bands" by Y-coordinate.
 *           Blocks whose vertical centres are within ROW_BAND_PX of each
 *           other are considered the same row.
 *   Pass 2: Within each band, sort blocks left→right by X.
 *   Result: Text flows in natural reading order regardless of column count.
 *
 * TABLE CELLS (accused table, property table):
 *   Table cells are already handled by band-sort — each row of the table
 *   becomes one band, cells are joined with " | " so the LLM sees:
 *   "1 | NIKHIL KUMAR | Father:HARISH KUMAR | 7/37 Ramesh Nagar..."
 *   instead of fragments scattered across the page.
 */
class OcrEngine(private val context: Context) {

    private val latinRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    private val devanagariRecognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )

    companion object {
        /**
         * Vertical tolerance in pixels (at 3× render scale) for grouping
         * text blocks into the same horizontal band.
         *
         * FIR form rows are roughly 60–90px tall at 3× so 40px gives clean
         * row separation without splitting same-row fields into different bands.
         */
        private const val ROW_BAND_PX = 40
    }

    suspend fun initialize() {
        Log.d("OCR", "ML Kit OCR ready (Latin + Devanagari) — spatial sort enabled")
    }

    // ── Extract text from a PDF file ──────────────────────────────────────────
    suspend fun extractTextFromPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw RuntimeException("Cannot open PDF file")

        val renderer = PdfRenderer(fd)
        val sb = StringBuilder()

        Log.d("OCR", "Processing PDF: ${renderer.pageCount} pages")

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)

            val width  = page.width  * 4
            val height = page.height * 3
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            // Use spatial OCR instead of raw text
            val pageText = ocrBitmapSpatial(bitmap)
            bitmap.recycle()

            Log.d("OCR", "Page ${i + 1}: extracted ${pageText.length} chars")
            if (pageText.isNotBlank()) {
                sb.append("=== Page ${i + 1} ===\n")
                sb.append(pageText)
                sb.append("\n\n")
            }
        }

        renderer.close()
        fd.close()

        val result = sb.toString().trim()

        if (result.isNotBlank()) {
            Log.d("OCR_RESULT", "Full Extraction Start:")
            result.chunked(1000).forEach { Log.d("OCR_RESULT", it) }
            Log.d("OCR_RESULT", "Full Extraction End.")
        } else {
            Log.e("OCR_RESULT", "OCR Result is EMPTY")
        }

        if (result.isBlank()) {
            throw RuntimeException(
                "OCR produced no text. Ensure ML Kit models are downloaded."
            )
        }
        result
    }

    // ── Spatial OCR: returns text in natural reading order ───────────────────
    /**
     * Runs ML Kit OCR, then re-orders the returned [Text.TextBlock]s by
     * spatial position rather than using [Text.text] directly.
     *
     * Steps:
     * 1. Run Devanagari recogniser (handles Hindi + English + digits).
     * 2. Collect every [Text.Line] with its bounding box centre-Y.
     * 3. Sort lines into horizontal bands (Y within ROW_BAND_PX → same band).
     * 4. Within each band sort left→right by bounding box left edge.
     * 5. Join band lines with "  |  " (helps LLM identify table columns).
     * 6. Join bands with newline.
     */
    suspend fun ocrBitmapSpatial(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val visionText = try {
            val devResult = devanagariRecognizer.process(image).await()
            if (devResult.text.isNotBlank()) devResult
            else latinRecognizer.process(image).await()
        } catch (e: Exception) {
            Log.e("OCR", "OCR failed: ${e.message}")
            return@withContext ""
        }

        // Collect all lines with their bounding boxes
        data class LineBox(val text: String, val left: Int, val top: Int, val centreY: Int)

        val allLines = mutableListOf<LineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isBlank()) continue
                allLines.add(
                    LineBox(
                        text    = text,
                        left    = box.left,
                        top     = box.top,
                        centreY = (box.top + box.bottom) / 2
                    )
                )
            }
        }

        if (allLines.isEmpty()) return@withContext ""

        // Sort all lines by centreY first so band grouping is stable
        allLines.sortBy { it.centreY }

        // Group into horizontal bands
        val bands = mutableListOf<MutableList<LineBox>>()
        for (line in allLines) {
            val lastBand = bands.lastOrNull()
            if (lastBand == null || line.centreY - lastBand.first().centreY > ROW_BAND_PX) {
                bands.add(mutableListOf(line))
            } else {
                lastBand.add(line)
            }
        }

        // Within each band, sort left→right
        val result = StringBuilder()
        for (band in bands) {
            band.sortBy { it.left }
            // Join cells in the same row with a separator the LLM can parse
            val rowText = band.joinToString("  |  ") { it.text }
            result.appendLine(rowText)
        }

        result.toString().trim()
    }

    // ── Simple bitmap OCR (kept for audio/camera use cases) ──────────────────
    suspend fun ocrBitmap(bitmap: Bitmap): String = ocrBitmapSpatial(bitmap)

    // ── Release ───────────────────────────────────────────────────────────────
    fun release() {
        try {
            latinRecognizer.close()
            devanagariRecognizer.close()
        } catch (e: Exception) {
            Log.e("OCR", "Error closing recognizers: ${e.message}")
        }
    }
}
package dev.mias.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Extracts text from a PDF entirely on-device.
 *
 * Two tiers, both offline:
 *  1. **Text layer** (PdfBox) — fast, exact, for normal/digital PDFs.
 *  2. **OCR fallback** (ML Kit, bundled model) — when a PDF has little/no text
 *     layer (i.e. it's scanned/image-only), each page is rendered and read.
 *
 * Call from a background thread; OCR of many pages is slow.
 */
object PdfTextExtractor {

    @Volatile
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }

    fun extract(context: Context, uri: Uri): String {
        ensureInit(context)

        val direct = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc).trim() }
            }
        }.getOrNull().orEmpty()

        // Enough real text? Use it. Otherwise fall back to OCR (scanned PDF).
        if (direct.length >= MIN_TEXT_LAYER_CHARS) return direct

        val ocr = runCatching { ocr(context, uri) }.getOrNull().orEmpty()
        return if (ocr.length > direct.length) ocr else direct
    }

    /** Render each page and run on-device OCR. Bounded by [MAX_OCR_PAGES]. */
    private fun ocr(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, MAX_OCR_PAGES)
                    val out = StringBuilder()
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val bitmap = Bitmap.createBitmap(
                                (page.width * RENDER_SCALE).toInt().coerceAtLeast(1),
                                (page.height * RENDER_SCALE).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            // White background so anti-aliased text reads cleanly.
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val text = runCatching {
                                Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
                            }.getOrNull().orEmpty()
                            bitmap.recycle()
                            if (text.isNotBlank()) out.append(text).append('\n')
                        }
                    }
                    return out.toString().trim()
                }
            }
            return ""
        } finally {
            runCatching { recognizer.close() }
        }
    }

    /** Below this many characters of text layer we assume the PDF is scanned. */
    private const val MIN_TEXT_LAYER_CHARS = 40

    /** Cap OCR work so a huge scanned book can't grind for minutes. */
    private const val MAX_OCR_PAGES = 30

    /** Upscale pages for sharper OCR without exploding memory. */
    private const val RENDER_SCALE = 2f
}

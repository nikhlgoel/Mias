package dev.mias.app.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * Extracts plain text from a PDF entirely on-device (PdfBox-Android — no
 * network, no cloud). Call from a background thread; large PDFs take time.
 */
object PdfTextExtractor {

    @Volatile
    private var initialized = false

    /** PdfBox needs a one-time resource init bound to the app context. */
    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }

    fun extract(context: Context, input: InputStream): String {
        ensureInit(context)
        return PDDocument.load(input).use { document ->
            PDFTextStripper().getText(document).trim()
        }
    }
}

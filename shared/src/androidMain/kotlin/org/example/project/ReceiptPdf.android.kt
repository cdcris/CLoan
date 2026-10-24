package org.example.project

import android.content.Context
import java.io.File

object ReceiptPdfStorage {
    lateinit var context: Context
    fun isInitialized(): Boolean = ::context.isInitialized
}

internal actual fun saveOfficialReceiptPdf(fileName: String, pdfBytes: ByteArray): String {
    if (!ReceiptPdfStorage.isInitialized()) return "PDF could not be saved: app context is unavailable."
    val directory = File(ReceiptPdfStorage.context.filesDir, "receipts")
    if (!directory.exists() && !directory.mkdirs()) return "PDF could not be saved."
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = File(directory, safeName)
    file.writeBytes(pdfBytes)
    return "PDF saved: ${file.absolutePath}"
}

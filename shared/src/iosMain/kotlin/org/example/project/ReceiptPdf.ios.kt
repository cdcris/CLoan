package org.example.project

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal actual fun saveOfficialReceiptPdf(fileName: String, pdfBytes: ByteArray): String {
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val path = NSTemporaryDirectory() + safeName
    val pdfText = pdfBytes.decodeToString()
    val saved = NSString.create(string = pdfText).writeToFile(
        path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    return if (saved) "PDF saved: $path" else "PDF could not be saved."
}

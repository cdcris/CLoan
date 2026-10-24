package org.example.project

internal expect fun saveOfficialReceiptPdf(fileName: String, pdfBytes: ByteArray): String

internal fun buildPdfDocument(lines: List<String>): ByteArray {
    val pages = (if (lines.isEmpty()) listOf("") else lines).chunked(22)
    val pdf = buildString {
        append("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        fun addObject(body: String) {
            offsets.add(length)
            append((offsets.size - 1).toString() + " 0 obj\n" + body + "\nendobj\n")
        }
        addObject("<< /Type /Catalog /Pages 2 0 R >>")
        val pageIds = pages.indices.map { 4 + it * 2 }
        val kids = pageIds.joinToString(" ") { it.toString() + " 0 R" }
        addObject("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size + " >>")
        addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        pages.forEachIndexed { pageIndex, pageLines ->
            val pageId = pageIds[pageIndex]
            val contentId = pageId + 1
            addObject("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents " + contentId + " 0 R >>")
            val content = buildString {
                append("BT\n/F1 16 Tf\n50 748 Td\n")
                pageLines.forEachIndexed { lineIndex, line ->
                    if (lineIndex > 0) append("0 -30 Td\n/F1 10 Tf\n")
                    append("(").append(pdfText(line)).append(") Tj\n")
                }
                append("ET\n")
            }
            addObject("<< /Length " + content.length + " >>\nstream\n" + content + "endstream")
        }
        val xrefOffset = length
        append("xref\n0 " + offsets.size + "\n0000000000 65535 f \n")
        offsets.drop(1).forEach { offset -> append(offset.toString().padStart(10, '0')).append(" 00000 n \n") }
        append("trailer\n<< /Size " + offsets.size + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF")
    }
    return pdf.encodeToByteArray()
}

private fun pdfText(value: String): String = value
    .replace("₱", "PHP ")
    .map { character ->
        when {
            character == '(' || character == ')' || character == '\\' -> "\\$character"
            character.code in 32..126 -> character.toString()
            else -> "?"
        }
    }
    .joinToString("")

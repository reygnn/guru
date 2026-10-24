package com.github.reygnn.guru

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A report saved as a ZIP in Download/Guru, through MediaStore: no permission, no manifest entry.
 * One ZIP per report, one entry in it — the report's ACRA JSON, unchanged. Once saved, the file is
 * the user's; Guru removes its own copy (hard rule 7).
 */
internal object GuruDownloads {

    /**
     * Under Download (Environment.DIRECTORY_DOWNLOADS, spelled out: a constant, and readable on the
     * plain JVM): the user's folder, not Guru's — files there are never read or removed again.
     */
    const val RELATIVE_PATH = "Download/Guru"

    // Digits stay ASCII in any locale: DateTimeFormatter's default DecimalStyle is STANDARD.
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)

    /**
     * App name, version, crash time in [zone]: "Verso-0.3.1-2026-10-24_14-03-12". Whatever is
     * not a letter, digit, dot or dash becomes `_`; an empty part is left out. A second report of
     * the same second gets MediaStore's " (1)".
     */
    fun baseName(label: String, report: GuruReport, zone: ZoneId): String =
        listOf(safe(label), safe(report.env.versionName), STAMP.format(Instant.ofEpochMilli(report.crashedAt).atZone(zone)))
            .filter { it.isNotEmpty() }
            .joinToString("-")

    private fun safe(part: String): String =
        part.trim().map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '_' }.joinToString("")

    /** The ZIP's bytes: one entry, [base].json, dated [time]. */
    fun zip(base: String, json: String, time: Long): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("$base.json").apply { this.time = time })
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    /**
     * Saves [bytes] as [base].zip; the file shows only once complete (IS_PENDING). Null when it
     * could not be saved — then nothing is left behind.
     */
    // anything the provider throws means "not saved", and the report stays
    @Suppress("TooGenericExceptionCaught")
    fun save(resolver: ContentResolver, base: String, bytes: ByteArray, log: (String, Throwable?) -> Unit): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$base.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var uri: Uri? = null
        try {
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null && publish(resolver, uri, bytes)) return uri
            log("saving $base.zip refused", null)
        } catch (e: Exception) {
            log("saving $base.zip failed", e)
        }
        uri?.let { discard(resolver, it, log) }
        return null
    }

    /** The bytes into the pending file, then shown; false when the provider says no. */
    private fun publish(resolver: ContentResolver, uri: Uri, bytes: ByteArray): Boolean {
        val out = resolver.openOutputStream(uri) ?: return false
        out.use { it.write(bytes) }
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        return resolver.update(uri, done, null, null) == 1
    }

    // best effort: a half-written pending file is MediaStore's to sweep if this fails too
    @Suppress("TooGenericExceptionCaught")
    private fun discard(resolver: ContentResolver, uri: Uri, log: (String, Throwable?) -> Unit) {
        try {
            resolver.delete(uri, null, null)
        } catch (e: Exception) {
            log("removing an unfinished zip failed", e)
        }
    }

    const val MIME = "application/zip"
}

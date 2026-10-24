package com.github.reygnn.guru

import java.io.InputStream

/**
 * The Amiga quote, the report's own wording for a developer (the share's subject), and the size
 * limits on what Guru keeps. What a user reads or hears is [GuruTexts], from the host.
 */
internal object GuruText {

    const val SOFTWARE_FAILURE = "Software Failure."
    const val PRESS_LEFT = "Press left mouse button to continue."

    /** A trace kept on disk: a StackOverflowError or an ANR dump stays well below this. */
    const val MAX_TRACE_CHARS = 256 * 1024

    /** An ANR dump read from the system: bytes, before decoding. */
    const val MAX_DUMP_BYTES = 256 * 1024

    /** The alert box: the Amiga's one line, folded for a portrait phone; then where the reports went. */
    fun screenLines(number: String, saved: Int, texts: GuruTexts): List<String> = listOf(
        SOFTWARE_FAILURE,
        PRESS_LEFT,
        "Guru Meditation $number",
        texts.saved(saved),
    )

    fun subject(report: GuruReport): String =
        "Guru Meditation ${report.number} · ${report.env.packageName} ${report.env.versionName}"

    /** At most [max] chars of [text] — never half a surrogate pair — and how much was cut. */
    fun cap(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = if (max > 0 && text[max - 1].isHighSurrogate()) max - 1 else max
        return text.substring(0, cut) + "\n[${text.length - cut} chars cut]"
    }

    /** At most [maxBytes] of [input] as UTF-8; a sequence split at the cut decodes as U+FFFD. */
    fun readCapped(input: InputStream, maxBytes: Int): String {
        val bytes = input.readNBytes(maxBytes + 1)
        if (bytes.size <= maxBytes) return String(bytes, Charsets.UTF_8)
        return String(bytes, 0, maxBytes, Charsets.UTF_8) + "\n[cut at $maxBytes bytes]"
    }
}

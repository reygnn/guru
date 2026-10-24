package com.github.reygnn.guru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipInputStream

/** The file name and the ZIP's bytes; saving through MediaStore is GuruSaveTest's. */
class GuruDownloadsTest {

    @get:Rule val locales = GuruLocaleRule()

    private val utc = ZoneOffset.UTC
    private val at = LocalDateTime.of(2026, 10, 24, 14, 3, 12).toInstant(utc).toEpochMilli()
    private val crash = report(crashedAt = at, env = testEnv.copy(versionName = "0.3.1"))

    @Test
    fun `name, version and crash time`() {
        assertEquals("Verso-0.3.1-2026-10-24_14-03-12", GuruDownloads.baseName("Verso", crash, utc))
    }

    @Test
    fun `the crash time is local`() {
        assertEquals("Verso-0.3.1-2026-10-24_16-03-12", GuruDownloads.baseName("Verso", crash, ZoneId.of("Europe/Zurich")))
    }

    @Test
    fun `what a file name should not carry becomes an underscore - letters stay`() {
        assertEquals("Guru_Sample-0.3.1-2026-10-24_14-03-12", GuruDownloads.baseName(" Guru Sample ", crash, utc))
        assertEquals("a_b_c_-0.3.1-2026-10-24_14-03-12", GuruDownloads.baseName("a/b:c*", crash, utc))
        assertEquals("Grüße-0.3.1-2026-10-24_14-03-12", GuruDownloads.baseName("Grüße", crash, utc))
        assertEquals("Verso-1.0_beta-2026-10-24_14-03-12", GuruDownloads.baseName("Verso", report(crashedAt = at,
            env = testEnv.copy(versionName = "1.0 beta")), utc))
    }

    @Test
    fun `an empty part is left out`() {
        val noVersion = report(crashedAt = at, env = testEnv.copy(versionName = ""))
        assertEquals("Verso-2026-10-24_14-03-12", GuruDownloads.baseName("Verso", noVersion, utc))
        assertEquals("2026-10-24_14-03-12", GuruDownloads.baseName("", noVersion, utc))
    }

    @Test
    fun `digits stay ASCII under any locale`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        assertEquals("Verso-0.3.1-2026-10-24_14-03-12", GuruDownloads.baseName("Verso", crash, utc))
    }

    @Test
    fun `the ZIP holds one entry - the JSON byte for byte, dated`() {
        val json = """{"STACK_TRACE":"Grüße 😀"}"""

        val bytes = GuruDownloads.zip("Verso-0.3.1-2026-10-24_14-03-12", json, at)

        ZipInputStream(bytes.inputStream()).use { zip ->
            val entry = zip.nextEntry!!
            assertEquals("Verso-0.3.1-2026-10-24_14-03-12.json", entry.name)
            assertEquals(at / 2000, entry.time / 2000)
            assertEquals(json, zip.readBytes().toString(Charsets.UTF_8))
            assertNull(zip.nextEntry)
        }
    }
}

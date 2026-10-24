package com.github.reygnn.guru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GuruTextTest {

    // ---- cap ---------------------------------------------------------------

    @Test
    fun `cap leaves a text of exactly max alone`() {
        assertEquals("abcde", GuruText.cap("abcde", 5))
    }

    @Test
    fun `cap cuts one past max and says how much`() {
        assertEquals("abcde\n[1 chars cut]", GuruText.cap("abcdef", 5))
    }

    /** "a😀b": cutting at 2 would keep the high half of the emoji alone. */
    @Test
    fun `cap never keeps half a surrogate pair`() {
        assertEquals("a\n[3 chars cut]", GuruText.cap("a😀b", 2))
        assertEquals("a😀\n[1 chars cut]", GuruText.cap("a😀b", 3))
    }

    // ---- readCapped -------------------------------------------------------------

    @Test
    fun `readCapped reads exactly max bytes whole`() {
        assertEquals("abcd", GuruText.readCapped(ByteArrayInputStream("abcd".toByteArray()), 4))
    }

    @Test
    fun `readCapped cuts one byte past max`() {
        assertEquals("abcd\n[cut at 4 bytes]", GuruText.readCapped(ByteArrayInputStream("abcde".toByteArray()), 4))
    }

    // ---- screen and messages -------------------------------------------------------

    @Test
    fun `one report is the three alert lines`() {
        assertEquals(
            listOf("Software Failure.", "Press left mouse button to continue.", "Guru Meditation #80000001.0000002A"),
            GuruText.screenLines("#80000001.0000002A", pending = 1, GuruTexts.English),
        )
    }

    @Test
    fun `more reports add the host's word for how many more`() {
        val counted = object : GuruTexts by GuruTexts.English {
            override fun more(count: Int) = "noch $count"
        }
        assertEquals("noch 2", GuruText.screenLines("#1", pending = 3, counted).last())
        assertEquals(3, GuruText.screenLines("#1", pending = 1, counted).size)
    }

    @Test
    fun `a share carries each report under its number`() {
        val share = GuruText.share(listOf(report(id = "a"), report(id = "b"))) { "{${it.id}}" }
        val number = report().number
        assertEquals("Guru Meditation $number\n{a}\n\nGuru Meditation $number\n{b}", share.text)
        assertEquals(listOf("a", "b"), share.ids)
    }

    @Test
    fun `a share takes whole reports while they fit and names only those`() {
        val head = "Guru Meditation ${report().number}\n".length
        val half = GuruText.MAX_SHARE_CHARS / 2
        // a and b fill the limit to the char, separator included; c no longer fits
        val sizes = mapOf("a" to half - head - 1, "b" to half - head - 1, "c" to 1)
        val share = GuruText.share(listOf("a", "b", "c").map { report(id = it) }) { "x".repeat(sizes.getValue(it.id)) }

        assertEquals(listOf("a", "b"), share.ids)
        assertEquals(GuruText.MAX_SHARE_CHARS, share.text.length)
        assertFalse(share.text.contains("chars cut"))
    }

    @Test
    fun `a first report too big alone goes capped, the rest stays`() {
        val share = GuruText.share(listOf(report(id = "a"), report(id = "b"))) { "x".repeat(GuruText.MAX_SHARE_CHARS) }

        assertEquals(listOf("a"), share.ids)
        assertTrue(share.text.endsWith("chars cut]"))
    }

    @Test
    fun `the share limit leaves room for three copies in one Binder transaction`() {
        assertTrue(3 * 2 * GuruText.MAX_SHARE_CHARS < 1024 * 1024 * 3 / 4)
    }

    @Test
    fun `the subject names number, package and version`() {
        val r = report()
        assertEquals("Guru Meditation ${r.number} · com.example.app 1.2.3", GuruText.subject(r))
    }
}

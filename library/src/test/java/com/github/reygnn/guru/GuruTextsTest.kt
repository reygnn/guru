package com.github.reygnn.guru

import org.junit.Assert.assertEquals
import org.junit.Test

/** The English default; a host's own texts are its resources, tested where they live. */
class GuruTextsTest {

    private val english = GuruTexts.English

    @Test
    fun `all saved, one or several, says where`() {
        assertEquals("Guru: report saved to Download/Guru", english.saved(1, 1))
        assertEquals("Guru: 3 reports saved to Download/Guru", english.saved(3, 3))
    }

    @Test
    fun `none saved is kept`() {
        assertEquals("Guru: not saved, kept for the next start", english.saved(0, 1))
        assertEquals("Guru: not saved, kept for the next start", english.saved(0, 2))
    }

    /** Both neighbours of "all": one short of it is partial, not "all saved". */
    @Test
    fun `some saved says how many of how many`() {
        assertEquals("Guru: 1 of 2 saved to Download/Guru, the rest kept", english.saved(1, 2))
        assertEquals("Guru: 2 of 3 saved to Download/Guru, the rest kept", english.saved(2, 3))
    }

    @Test
    fun `more counts the reports behind the one shown`() {
        assertEquals("+1 more", english.more(1))
    }

    @Test
    fun `the screen's words name both gestures`() {
        assertEquals("double-tap: continue · hold: save · back: later", english.hint)
        assertEquals("continue, discard the report", english.actionContinue)
        assertEquals("save the report to Download/Guru", english.actionSave)
    }

    @Test
    fun `a host's texts that override nothing are English`() {
        val bare = object : GuruTexts {}
        assertEquals(english.hint, bare.hint)
        assertEquals(english.actionContinue, bare.actionContinue)
        assertEquals(english.actionSave, bare.actionSave)
        assertEquals(english.more(2), bare.more(2))
        assertEquals(english.saved(1, 2), bare.saved(1, 2))
    }
}

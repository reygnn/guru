package com.github.reygnn.guru.sample

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SampleGuruTextsTest {

    private val texts get() = SampleGuruTexts(RuntimeEnvironment.getApplication())

    @Test
    fun `English by default`() {
        assertEquals("double-tap: continue · hold: save · back: later", texts.hint)
        assertEquals("+1 more", texts.more(1))
        assertEquals("Guru: 1 report saved to Download/Guru", texts.saved(1, 1))
        assertEquals("Guru: 3 reports saved to Download/Guru", texts.saved(3, 3))
        assertEquals("Guru: not saved, kept for the next start", texts.saved(0, 2))
        assertEquals("Guru: 1 of 2 saved to Download/Guru, the rest kept", texts.saved(1, 2))
    }

    @Test
    @Config(qualifiers = "de")
    fun `German from values-de`() {
        assertEquals("doppeltippen: weiter · halten: speichern · zurück: später", texts.hint)
        assertEquals("weiter, Bericht verwerfen", texts.actionContinue)
        assertEquals("Bericht in Download/Guru speichern", texts.actionSave)
        assertEquals("+2 weitere", texts.more(2))
        assertEquals("Guru: 1 von 2 in Download/Guru gespeichert, der Rest bleibt", texts.saved(1, 2))
    }
}

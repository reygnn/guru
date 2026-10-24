package com.github.reygnn.guru

import android.app.Dialog
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuruScreenTest {

    @get:Rule val host = GuruHostRule()

    private object German : GuruTexts {
        override val hint = "doppeltippen: schließen · halten: teilen"
        override val actionClose = "schließen"
        override val actionShare = "Bericht teilen"
        override fun saved(count: Int) = "$count gespeichert"
    }

    private var shared = 0
    private val lines = listOf(GuruText.SOFTWARE_FAILURE, GuruText.PRESS_LEFT, "Guru Meditation #80000001.4A3F21C0")

    private val controller = lazy { host.activity() }

    private fun show() = GuruScreen.show(controller.value.get(), lines, German, onShare = { shared++ })

    private fun view(dialog: Dialog) = dialog.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

    @Test
    fun `close shares nothing, and a later hold neither`() {
        val dialog = show()
        val view = view(dialog)

        view.performClick()
        view.performLongClick()

        assertEquals(0, shared)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `share acts once`() {
        val dialog = show()
        val view = view(dialog)

        assertTrue(view.performLongClick())
        view.performLongClick()
        view.performClick()

        assertEquals(1, shared)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun `TalkBack names both actions in the host's words`() {
        val info = view(show()).createAccessibilityNodeInfo()

        val labels = info.actionList.associate { it.id to it.label?.toString() }
        assertEquals(German.actionClose, labels[AccessibilityNodeInfo.ACTION_CLICK])
        assertEquals(German.actionShare, labels[AccessibilityNodeInfo.ACTION_LONG_CLICK])
    }

    @Test
    fun `TalkBack reads the lines and the host's hint`() {
        val said = view(show()).contentDescription.toString()

        assertEquals(
            "Software Failure. Press left mouse button to continue. Guru Meditation #80000001.4A3F21C0. " + German.hint,
            said,
        )
    }

    @Test
    fun `the alert closes with its activity`() {
        val dialog = show()
        assertTrue(dialog.isShowing)

        controller.value.pause().stop().destroy()

        assertFalse(dialog.isShowing)
        assertEquals(0, shared)
    }
}

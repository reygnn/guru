package com.github.reygnn.guru

import android.app.Dialog
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/** The alert: only what this start saved, once; it decides nothing. */
@RunWith(RobolectricTestRunner::class)
class GuruShowPendingTest {

    @get:Rule val host = GuruHostRule()

    private val a = "0b6c2f8e-1d2a-4c3b-9e8f-00000000000a"
    private val b = "0b6c2f8e-1d2a-4c3b-9e8f-00000000000b"

    private lateinit var media: FakeMediaStore

    @Before
    fun setUp() {
        media = FakeMediaStore.install()
    }

    /** install, the exit history marked; its io steps still queued. */
    private fun installed() {
        Guru.install(host.app, host.io)
        host.store.markExitsSeen(System.currentTimeMillis() - GuruHostRule.MINUTE_MS)
    }

    private fun Dialog.guruView(): View = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)

    private fun shown(): Dialog {
        installed()
        Guru.showPending(host.activity().get()) { it() }
        host.runIo()
        return ShadowDialog.getLatestDialog()
    }

    @Test
    fun `nothing saved shows nothing`() {
        installed()
        Guru.showPending(host.activity().get()) { it() }
        host.runIo()

        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `a report not saved is not shown - it waits for the next start`() {
        host.crashed(a)
        media.refuse = setOf(1)
        installed()
        Guru.showPending(host.activity().get()) { it() }

        host.runIo()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(listOf(a), host.store.pending().map { it.id })
    }

    @Test
    fun `the alert comes after the save, on the host's main`() {
        host.crashed(a)
        installed()
        val onUi = mutableListOf<() -> Unit>()
        Guru.showPending(host.activity().get()) { onUi += it }

        assertEquals(3, host.queuedIo())
        host.runIo()
        assertEquals(1, media.rows.size)
        assertNull(ShadowDialog.getLatestDialog())

        onUi.single()()
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
    }

    @Test
    fun `the alert shows the newest report's number and how many were saved`() {
        val older = report(id = a, crashedAt = System.currentTimeMillis() - 2 * GuruHostRule.MINUTE_MS,
            trace = "java.lang.IllegalArgumentException: old\n\tat com.example.app.Old.run(Old.kt:1)").also { host.store.write(it) }
        val newest = host.crashed(b)

        val said = shown().guruView().contentDescription.toString()

        assertTrue(older.number != newest.number)
        assertTrue(said, said.contains("Guru Meditation ${newest.number}"))
        assertFalse(said, said.contains(older.number))
        assertTrue(said, said.contains(GuruTexts.English.saved(2)))
    }

    @Test
    fun `the alert comes once - a second call shows nothing`() {
        host.crashed(a)
        shown().dismiss()

        Guru.showPending(host.activity().get()) { it() }
        host.runIo()

        assertFalse(ShadowDialog.getLatestDialog().isShowing)
        assertEquals(1, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `a double-tap closes it, and that is all`() {
        host.crashed(a)
        val dialog = shown()

        dialog.guruView().performClick()

        assertFalse(dialog.isShowing)
        assertEquals(0, host.queuedIo())
        assertEquals(1, media.rows.size)
        assertNull(shadowOf(host.activity().get()).nextStartedActivity)
    }

    @Test
    fun `Back closes it`() {
        host.crashed(a)
        val dialog = shown()

        host.idleMain()
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        host.idleMain()

        assertFalse(dialog.isShowing)
        assertEquals(0, host.queuedIo())
    }

    @Test
    fun `a destroyed activity gets no alert, the file is saved anyway`() {
        host.crashed(a)
        installed()
        val controller = host.activity()
        val onUi = mutableListOf<() -> Unit>()
        Guru.showPending(controller.get()) { onUi += it }
        host.runIo()

        controller.pause().stop().destroy()
        onUi.single()()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(1, media.rows.size)
        assertTrue(host.store.pending().isEmpty())
    }

    @Test
    fun `a finishing activity gets no alert`() {
        host.crashed(a)
        installed()
        val activity = host.activity().get()
        val onUi = mutableListOf<() -> Unit>()
        Guru.showPending(activity) { onUi += it }
        host.runIo()

        activity.finish()
        onUi.single()()

        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `a start without any alert still saves`() {
        host.crashed(a)

        installed()
        host.runIo()

        assertEquals(1, media.rows.size)
        assertTrue(host.store.pending().isEmpty())
    }
}

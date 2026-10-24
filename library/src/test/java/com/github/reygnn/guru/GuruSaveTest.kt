package com.github.reygnn.guru

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.TransactionTooLargeException
import android.provider.MediaStore
import android.view.ViewGroup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.time.ZoneId
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class GuruSaveTest {

    @get:Rule val host = GuruHostRule()

    private val a = "0b6c2f8e-1d2a-4c3b-9e8f-00000000000a"
    private val b = "0b6c2f8e-1d2a-4c3b-9e8f-00000000000b"

    private lateinit var media: FakeMediaStore

    @Before
    fun setUp() {
        media = FakeMediaStore.install()
    }

    /** An activity the system refuses to start anything for, as with a chooser it cannot show. */
    class RefusingActivity : Activity() {
        // what the system throws there: a plain RuntimeException around the Binder's refusal
        @Suppress("TooGenericExceptionThrown")
        override fun startActivity(intent: Intent?) {
            throw RuntimeException("Failure from system", TransactionTooLargeException("data parcel size 1234567 bytes"))
        }
    }

    /**
     * The alert for what is pending now, held — the save queued on io, not run. The host's
     * onUi posts to main, as a real host's does.
     */
    private fun hold(activity: Activity = host.activity().get()): Activity {
        Guru.install(host.app, host.io)
        host.store.markExitsSeen(System.currentTimeMillis() - GuruHostRule.MINUTE_MS)
        Guru.showPending(activity) { activity.mainExecutor.execute(it) }
        host.runIo()
        host.idleMain()
        ShadowDialog.getLatestDialog().findViewById<ViewGroup>(android.R.id.content).getChildAt(0).performLongClick()
        return activity
    }

    /** Hold, run the save, bring its offer to main: the chooser started, or null. */
    private fun holdAndSave(activity: Activity = host.activity().get()): Intent? {
        hold(activity)
        host.runIo()
        host.idleMain()
        return shadowOf(activity).nextStartedActivity
    }

    private fun Intent.offered(): Intent = getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

    private fun unzip(row: FakeMediaStore.Row): Pair<String, String> =
        ZipInputStream(row.file.inputStream()).use { zip ->
            val entry = zip.nextEntry!!
            val text = zip.readBytes().toString(Charsets.UTF_8)
            assertNull(zip.nextEntry)
            entry.name to text
        }

    private val label get() = host.app.applicationInfo.loadLabel(host.app.packageManager).toString()

    @Test
    fun `the save waits for the host's io`() {
        host.crashed(a)
        hold()

        assertEquals(1, host.queuedIo())
        assertTrue(media.rows.isEmpty())
        assertEquals(listOf(a), host.store.pending().map { it.id })
    }

    @Test
    fun `hold saves each report as a ZIP in Download-Guru and removes it`() {
        val older = host.crashed(a, at = System.currentTimeMillis() - 2 * GuruHostRule.MINUTE_MS)
        val newer = host.crashed(b)

        holdAndSave()

        val zone = ZoneId.systemDefault()
        assertEquals(
            listOf(newer, older).map { GuruDownloads.baseName(label, it, zone) + ".zip" },
            media.rows.map { it.values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) },
        )
        media.rows.forEach { row ->
            assertEquals("Download/Guru", row.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            assertEquals("application/zip", row.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        }
        assertTrue(host.store.pending().isEmpty())
        assertEquals(GuruTexts.English.saved(2, 2), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `the ZIP holds one entry - the report's ACRA JSON`() {
        val report = host.crashed(a)

        holdAndSave()

        val (name, json) = unzip(media.rows.single())
        assertEquals(GuruDownloads.baseName(label, report, ZoneId.systemDefault()) + ".json", name)
        assertEquals(a, JSONObject(json).getString("REPORT_ID"))
        assertEquals(report.number, JSONObject(json).getJSONObject("CUSTOM_DATA").getString("GURU_MEDITATION"))
    }

    @Test
    fun `an app without a label names its files by package`() {
        val report = host.crashed(a)
        host.app.applicationInfo.nonLocalizedLabel = " "

        holdAndSave()

        assertEquals(
            GuruDownloads.baseName(host.app.packageName, report, ZoneId.systemDefault()) + ".zip",
            media.rows.single().values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME),
        )
    }

    @Test
    fun `a refused save keeps the report and offers nothing`() {
        host.crashed(a)
        media.refuse = 1

        val chooser = holdAndSave()

        assertNull(chooser)
        assertEquals(listOf(a), host.store.pending().map { it.id })
        assertEquals(GuruTexts.English.saved(0, 1), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a failed write leaves no file and keeps the report`() {
        host.crashed(a)
        media.failWrite = true

        holdAndSave()

        assertTrue(media.rows.isEmpty())
        assertEquals(listOf(a), host.store.pending().map { it.id })
    }

    @Test
    fun `only the reports saved go - the rest stays`() {
        host.crashed(a, at = System.currentTimeMillis() - 2 * GuruHostRule.MINUTE_MS)
        host.crashed(b)
        media.refuse = 2

        val chooser = holdAndSave()!!

        assertEquals(listOf(a), host.store.pending().map { it.id })
        assertEquals(GuruTexts.English.saved(1, 2), ShadowToast.getTextOfLatestToast())
        assertEquals(Intent.ACTION_SEND, chooser.offered().action)
    }

    @Test
    fun `the chooser offers the saved file with read access`() {
        val report = host.crashed(a)

        val chooser = holdAndSave()!!

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.offered()
        val file = media.rows.single().uri
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/zip", send.type)
        assertEquals(file, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(file, send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(GuruText.subject(report), send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `several files are offered together`() {
        host.crashed(a, at = System.currentTimeMillis() - 2 * GuruHostRule.MINUTE_MS)
        host.crashed(b)

        val send = holdAndSave()!!.offered()

        val files = media.rows.map { it.uri }
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals(files, send.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(files, (0 until send.clipData!!.itemCount).map { send.clipData!!.getItemAt(it).uri })
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `a chooser that cannot start costs nothing - the file is saved`() {
        host.crashed(a)
        val refusing = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()

        holdAndSave(refusing)

        assertEquals(1, media.rows.size)
        assertTrue(host.store.pending().isEmpty())
        assertEquals(GuruTexts.English.saved(1, 1), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `an activity gone before the offer gets no chooser`() {
        host.crashed(a)
        val controller = host.activity()
        hold(controller.get())

        host.runIo()
        controller.pause().stop().destroy()
        host.idleMain()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertEquals(1, media.rows.size)
    }
}

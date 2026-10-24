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
import java.util.zip.ZipFile

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

    /** The one entry, through ZipFile: it needs the central directory, so a cut file fails here. */
    private fun unzip(row: FakeMediaStore.Row): Triple<String, String, Long> =
        ZipFile(row.file).use { zip ->
            val entry = zip.entries().toList().single()
            Triple(entry.name, zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8), entry.time)
        }

    private fun Intent.clipUris(): List<Uri> = (0 until clipData!!.itemCount).map { clipData!!.getItemAt(it).uri }

    private val label get() = host.app.applicationInfo.loadLabel(host.app.packageManager).toString()

    private val name get() = MediaStore.MediaColumns.DISPLAY_NAME

    /** Two reports, a older than b, with numbers of their own: pending (and saved) b first. */
    private fun twoReports(): Pair<GuruReport, GuruReport> {
        val older = report(id = a, crashedAt = System.currentTimeMillis() - 2 * GuruHostRule.MINUTE_MS,
            trace = "java.lang.IllegalArgumentException: old\n\tat com.example.app.Old.run(Old.kt:1)").also { host.store.write(it) }
        val newer = host.crashed(b)
        assertTrue(GuruText.subject(older) != GuruText.subject(newer))
        return older to newer
    }

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
        val (older, newer) = twoReports()

        holdAndSave()

        val zone = ZoneId.systemDefault()
        assertEquals(
            listOf(newer, older).map { GuruDownloads.baseName(label, it, zone) + ".zip" },
            media.rows.map { it.values.getAsString(name) },
        )
        media.rows.forEach { row ->
            assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI, row.collection)
            assertEquals("Download/Guru", row.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            assertEquals("application/zip", row.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals(0, row.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        }
        assertTrue(host.store.pending().isEmpty())
        assertEquals(GuruTexts.English.saved(2, 2), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a file is hidden until all its bytes are in`() {
        host.crashed(a)

        holdAndSave()

        val row = media.rows.single()
        assertEquals(1, row.inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertEquals(row.file.length(), row.sizeAtPublish)
        assertTrue(row.file.length() > 0)
    }

    @Test
    fun `the ZIP holds one entry - the report's ACRA JSON, dated at the crash`() {
        val report = host.crashed(a, at = System.currentTimeMillis() - 7 * 24 * 60 * GuruHostRule.MINUTE_MS)

        holdAndSave()

        val (entry, json, time) = unzip(media.rows.single())
        assertEquals(GuruDownloads.baseName(label, report, ZoneId.systemDefault()) + ".json", entry)
        assertEquals(a, JSONObject(json).getString("REPORT_ID"))
        assertEquals(report.number, JSONObject(json).getJSONObject("CUSTOM_DATA").getString("GURU_MEDITATION"))
        assertEquals(report.crashedAt / 2000, time / 2000)
    }

    @Test
    fun `each ZIP holds its own report`() {
        val (older, newer) = twoReports()

        holdAndSave()

        val zone = ZoneId.systemDefault()
        mapOf(newer to media.rows[0], older to media.rows[1]).forEach { (report, row) ->
            assertEquals(GuruDownloads.baseName(label, report, zone) + ".zip", row.values.getAsString(name))
            assertEquals(report.id, JSONObject(unzip(row).second).getString("REPORT_ID"))
        }
    }

    @Test
    fun `files are named after the app's label`() {
        host.crashed(a)
        host.app.applicationInfo.nonLocalizedLabel = "Guru Sample"

        holdAndSave()

        assertTrue(media.rows.single().values.getAsString(name).startsWith("Guru_Sample-"))
    }

    @Test
    fun `an app without a label names its files by package`() {
        val report = host.crashed(a)
        host.app.applicationInfo.nonLocalizedLabel = " "

        holdAndSave()

        assertEquals(
            GuruDownloads.baseName(host.app.packageName, report, ZoneId.systemDefault()) + ".zip",
            media.rows.single().values.getAsString(name),
        )
    }

    @Test
    fun `a refused save keeps the report and offers nothing`() {
        host.crashed(a)
        media.refuse = setOf(1)

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
    fun `a file the provider will not publish is not saved`() {
        host.crashed(a)
        media.refuseUpdate = true

        val chooser = holdAndSave()

        assertNull(chooser)
        assertTrue(media.rows.isEmpty())
        assertEquals(listOf(a), host.store.pending().map { it.id })
        assertEquals(GuruTexts.English.saved(0, 1), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a provider that throws keeps that report and saves the rest`() {
        val (older, newer) = twoReports()
        media.throwOn = setOf(1)

        holdAndSave()

        assertEquals(listOf(older.id), media.rows.map { JSONObject(unzip(it).second).getString("REPORT_ID") })
        assertEquals(listOf(newer.id), host.store.pending().map { it.id })
        assertEquals(GuruTexts.English.saved(1, 2), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `an unfinished file that cannot be removed still keeps the report`() {
        host.crashed(a)
        media.failWrite = true
        media.throwOnDelete = true

        holdAndSave()

        assertEquals(listOf(a), host.store.pending().map { it.id })
        assertEquals(GuruTexts.English.saved(0, 1), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `only the reports saved go - the subject names one of them`() {
        val (older, newer) = twoReports()
        media.refuse = setOf(1)

        val chooser = holdAndSave()!!

        assertEquals(listOf(newer.id), host.store.pending().map { it.id })
        assertEquals(GuruTexts.English.saved(1, 2), ShadowToast.getTextOfLatestToast())
        assertEquals(Intent.ACTION_SEND, chooser.offered().action)
        assertEquals(GuruText.subject(older), chooser.offered().getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `the chooser offers the saved file with read access, in a task of its own`() {
        val report = host.crashed(a)

        val chooser = holdAndSave()!!

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val send = chooser.offered()
        val file = media.rows.single().uri
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/zip", send.type)
        assertEquals(file, send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(listOf(file), send.clipUris())
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(GuruText.subject(report), send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `the chooser itself may read the files - for its preview`() {
        host.crashed(a)

        val chooser = holdAndSave()!!

        assertEquals(listOf(media.rows.single().uri), chooser.clipUris())
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `several files are offered together`() {
        val (_, newer) = twoReports()

        val chooser = holdAndSave()!!
        val send = chooser.offered()

        val files = media.rows.map { it.uri }
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals(files, send.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(files, send.clipUris())
        assertEquals(files, chooser.clipUris())
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(GuruText.subject(newer), send.getStringExtra(Intent.EXTRA_SUBJECT))
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

    @Test
    fun `an activity finishing before the offer gets no chooser`() {
        host.crashed(a)
        val activity = hold()

        host.runIo()
        activity.finish()
        host.idleMain()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(1, media.rows.size)
    }
}

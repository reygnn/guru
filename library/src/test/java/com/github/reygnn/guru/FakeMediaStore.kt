package com.github.reygnn.guru

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

/**
 * MediaStore for Robolectric, which has no MediaProvider: rows in memory, their bytes in real
 * files. Inserts are numbered from 1; [refuse] turns those down (null), [throwOn] throws on them.
 * [failWrite] refuses the stream, [refuseUpdate] publishes nothing (0 rows), [throwOnDelete]
 * makes removing an unfinished file throw.
 */
class FakeMediaStore : ContentProvider() {

    /** [inserted]: the values as inserted; [values]: as they are now; [sizeAtPublish]: the file's bytes when IS_PENDING went 0. */
    class Row(val collection: Uri, val uri: Uri, val inserted: ContentValues, val file: File) {
        val values = ContentValues(inserted)
        var sizeAtPublish: Long? = null
    }

    val rows = mutableListOf<Row>()
    var refuse = emptySet<Int>()
    var throwOn = emptySet<Int>()
    var failWrite = false
    var refuseUpdate = false
    var throwOnDelete = false
    private var inserts = 0

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        inserts++
        if (inserts in throwOn) throw IllegalStateException("insert $inserts thrown")
        if (inserts in refuse) return null
        val row = Row(uri, uri.buildUpon().appendPath(inserts.toString()).build(), ContentValues(values), File(context!!.cacheDir, "media-$inserts"))
        rows += row
        return row.uri
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val row = rows.find { it.uri == uri }
        if (row == null || refuseUpdate) return 0
        if (values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0) row.sizeAtPublish = row.file.length()
        row.values.putAll(values)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (throwOnDelete) throw IllegalStateException("delete thrown")
        return if (rows.removeIf { it.uri == uri }) 1 else 0
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val row = rows.find { it.uri == uri }
        if (row == null || failWrite) throw FileNotFoundException("no stream for $uri")
        return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode) or ParcelFileDescriptor.MODE_CREATE)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    companion object {
        fun install(): FakeMediaStore = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
    }
}

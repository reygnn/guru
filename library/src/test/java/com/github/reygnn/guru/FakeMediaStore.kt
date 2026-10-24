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
 * files. [refuse] turns inserts down from that row on (1-based); [failWrite] refuses the stream.
 */
class FakeMediaStore : ContentProvider() {

    class Row(val uri: Uri, val values: ContentValues, val file: File)

    val rows = mutableListOf<Row>()
    var refuse = Int.MAX_VALUE
    var failWrite = false
    private var inserts = 0

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        inserts++
        if (inserts >= refuse) return null
        val row = Row(uri.buildUpon().appendPath(inserts.toString()).build(), ContentValues(values), File(context!!.cacheDir, "media-$inserts"))
        rows += row
        return row.uri
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val row = rows.find { it.uri == uri } ?: return 0
        row.values.putAll(values)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (rows.removeIf { it.uri == uri }) 1 else 0

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

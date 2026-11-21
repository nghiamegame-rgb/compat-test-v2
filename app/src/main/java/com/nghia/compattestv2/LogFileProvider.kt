package com.nghia.compattestv2

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * ContentProvider tối giản, chỉ đọc, phục vụ bản sao log trong cacheDir/share
 * để chia sẻ qua ACTION_SEND (EXTRA_STREAM). Không export; quyền đọc được cấp
 * tạm thời qua FLAG_GRANT_READ_URI_PERMISSION.
 */
class LogFileProvider : ContentProvider() {

    companion object {
        const val SHARE_DIR = "share"
        fun authority(ctx: Context) = "${ctx.packageName}.logs"
        fun uriFor(ctx: Context, name: String): Uri =
            Uri.Builder().scheme("content").authority(authority(ctx)).appendPath(name).build()
        fun shareDir(ctx: Context) = File(ctx.cacheDir, SHARE_DIR)
    }

    override fun onCreate(): Boolean = true

    private fun fileFor(uri: Uri): File? {
        val ctx = context ?: return null
        val segs = uri.pathSegments
        if (segs.size != 1) return null
        val name = segs[0]
        if (name.contains('/') || name.contains("..")) return null
        val dir = shareDir(ctx).canonicalFile
        val f = File(dir, name).canonicalFile
        return if (f.parentFile == dir && f.isFile) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Chỉ cho phép đọc")
        val f = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
            .toTypedArray()
        val row = cols.map { if (it == OpenableColumns.DISPLAY_NAME) f.name else f.length() }.toTypedArray()
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String = "text/plain"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0
}

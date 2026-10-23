package com.app.dockeep.data.files

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns

/** Not a DocumentsProvider: never advertises the warehouse to system file browsers. */
class VaultProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = VaultFiles.mime(VaultFiles.file(requireNotNull(context), uri))
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = VaultFiles.file(requireNotNull(context), uri)
        require(file.isFile) { "文件不存在" }
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply { addRow(cols.map { when(it) {
            OpenableColumns.DISPLAY_NAME -> file.name
            OpenableColumns.SIZE -> file.length()
            else -> null
        } }.toTypedArray()) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "仅允许临时读取" }
        val file = VaultFiles.file(requireNotNull(context), uri)
        require(file.isFile) { "文件不存在" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}

package com.notaristo.docimagesaver

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.ArrayDeque

data class FolderSyncResult(val saved: Int, val skipped: Int, val failed: Int)

class WhatsAppFolderSync(private val context: Context) {
    fun sync(treeUri: Uri): FolderSyncResult {
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences("doc_image_saver", Context.MODE_PRIVATE)
        val imported = prefs.getStringSet("whatsapp_imported_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        val pending = ArrayDeque<Uri>()
        val images = mutableListOf<Uri>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        pending.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId))

        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            val documentId = DocumentsContract.getDocumentId(directory)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex) ?: ""
                    val mime = cursor.getString(mimeIndex) ?: ""
                    val child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(child)
                    } else if (mime.startsWith("image/") || isImageName(name)) {
                        images.add(child)
                    }
                }
            }
        }

        var saved = 0
        var skipped = 0
        var failed = 0
        val saver = ImageSaver(context)
        for (uri in images) {
            val key = uri.toString()
            if (key in imported) {
                skipped++
                continue
            }
            val result = saver.save(uri)
            if (result.success) {
                imported.add(key)
                saved++
            } else {
                failed++
            }
        }
        prefs.edit().putStringSet("whatsapp_imported_uris", imported).apply()
        return FolderSyncResult(saved, skipped, failed)
    }

    private fun isImageName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif")
}

package com.notaristo.docimagesaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.util.Locale

data class SaveResult(val fileName: String, val success: Boolean, val message: String, val uri: Uri? = null)

class ImageSaver(private val context: Context) {
    fun save(uri: Uri): SaveResult {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(uri)
        val reportedMime = resolver.getType(uri)
        val mimeType = when {
            reportedMime?.startsWith("image/") == true -> reportedMime
            reportedMime == null || reportedMime == "application/octet-stream" || reportedMime == "application/binary" ->
                originalName?.let(::mimeTypeFromName)
            else -> null
        } ?: return SaveResult(originalName ?: "Unknown file", false, "The shared item is not a supported image.")

        val originalName = originalName ?: ("Shared image" + extensionFor(mimeType))
        val safeName = makeSafeFileName(originalName)
        val displayName = uniqueDisplayName(safeName, mimeType)
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Document Images")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val destination = resolver.insert(collection, values)
            ?: return SaveResult(displayName, false, "Android could not create the gallery file.")

        return try {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("The source image could not be opened.")
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException("The destination image could not be opened.")
            input.use { source ->
                output.use { target ->
                    source.copyTo(target, DEFAULT_BUFFER_SIZE)
                    target.flush()
                }
            }
            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(destination, publishValues, null, null)
            SaveResult(displayName, true, "Saved to Pictures/Document Images", destination)
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            SaveResult(displayName, false, error.localizedMessage ?: "Could not save this image.")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }

    private fun makeSafeFileName(name: String): String {
        return name.substringAfterLast('/').filterNot { it.isISOControl() || it in "/\\\\:*?\"<>|" }
            .ifBlank { "Shared image" }
    }

    private fun uniqueDisplayName(name: String, mimeType: String): String {
        val dot = name.lastIndexOf('.')
        val hasExtension = dot > 0
        val base = if (hasExtension) name.substring(0, dot) else name
        val extension = if (hasExtension) name.substring(dot) else extensionFor(mimeType)
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var candidate = base + extension
        var suffix = 1
        while (context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                MediaStore.Images.Media.RELATIVE_PATH + "=? AND " + MediaStore.Images.Media.DISPLAY_NAME + "=?",
                arrayOf(Environment.DIRECTORY_PICTURES + "/Document Images/", candidate),
                null
            )?.use { it.moveToFirst() } == true
        ) {
            candidate = base + " (" + suffix + ")" + extension
            suffix++
        }
        return candidate
    }

    private fun mimeTypeFromName(name: String): String? {
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            else -> null
        }
    }

    private fun extensionFor(mimeType: String): String {
        return when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/heic" -> ".heic"
            "image/heif" -> ".heif"
            "image/bmp" -> ".bmp"
            "image/avif" -> ".avif"
            else -> ".img"
        }
    }
}

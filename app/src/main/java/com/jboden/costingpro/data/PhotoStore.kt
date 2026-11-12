package com.jboden.costingpro.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recipe photos, kept in the internal storage of the app. The database stores only the
 * path relative to the files directory (for example "photos/3f2a.jpg"), which stays valid
 * if the app data is restored somewhere else.
 */
class PhotoStore(private val context: Context) {

    /** A new, unused relative path for a photo. */
    fun newPath(): String = "$DIR/${UUID.randomUUID()}.jpg"

    /** The file behind a stored relative path. */
    fun file(path: String): File = File(context.filesDir, path)

    /** Creates the empty file for [path] and returns a content URI the camera app can write to. */
    fun cameraUri(path: String): Uri {
        val file = file(path)
        file.parentFile?.mkdirs()
        file.createNewFile()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Copies the picked image into internal storage. Returns its relative path, or null if it could not be read. */
    suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        val path = newPath()
        val target = file(path)
        try {
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(source) ?: return@withContext null
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            path
        } catch (e: IOException) {
            target.delete()
            null
        } catch (e: SecurityException) {
            target.delete()
            null
        }
    }

    /** True if the file exists and has content: a cancelled camera leaves an empty file. */
    fun hasPhoto(path: String): Boolean = file(path).let { it.isFile && it.length() > 0 }

    suspend fun delete(path: String?) {
        if (path == null) return
        withContext(Dispatchers.IO) { file(path).delete() }
    }

    private companion object {
        const val DIR = "photos"
    }
}

package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoExportStorage {

    data class SavedGalleryResult(
        val uri: Uri,
        val userFacingLocation: String
    )

    fun createCacheExportFile(context: Context, prefix: String = "RecapDub"): File {
        val dir = File(context.cacheDir, "dub_exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${prefix}_$stamp.mp4")
    }

    fun createTempAudioFile(context: Context, name: String = "dub_voiceover.wav"): File {
        val dir = File(context.cacheDir, "dub_exports").apply { mkdirs() }
        return File(dir, name).apply {
            if (exists()) delete()
        }
    }

    suspend fun saveVideoToDeviceGallery(
        context: Context,
        sourceFile: File
    ): SavedGalleryResult = withContext(Dispatchers.IO) {
        require(sourceFile.exists() && sourceFile.length() > 0L) {
            "Exported video file not found."
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "RecapScript_BurmeseDub_$stamp.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/RecapScript"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val insertedUri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry for download.")

            try {
                resolver.openOutputStream(insertedUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Could not open output stream for Gallery video.")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)

                SavedGalleryResult(
                    uri = insertedUri,
                    userFacingLocation = "Gallery • Movies/RecapScript/$displayName"
                )
            } catch (e: Exception) {
                runCatching { resolver.delete(insertedUri, null, null) }
                throw e
            }
        } else {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?.apply { mkdirs() }
                ?: File(context.filesDir, "Movies").apply { mkdirs() }
            val destFile = File(moviesDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)
            val shareUri = getShareableUri(context, destFile)
            SavedGalleryResult(
                uri = shareUri,
                userFacingLocation = "Movies/${destFile.name}"
            )
        }
    }

    fun getShareableUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun buildShareIntent(context: Context, file: File, savedGalleryUri: Uri?): Intent {
        val shareUri = savedGalleryUri ?: getShareableUri(context, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Dubbed Burmese Video")
    }
}

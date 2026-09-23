package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.local.entities.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class StorageService(private val context: Context) {

    companion object {
        private const val TAG = "StorageService"
        const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L // 100 MB configurable limit
    }

    private val attachmentsDir: File by lazy {
        File(context.filesDir, "attachments").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getSubDir(subName: String): File {
        return File(attachmentsDir, subName).apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun saveImageAttachment(
        uri: Uri,
        messageId: String,
        chatId: String,
        caption: String? = null
    ): Result<AttachmentEntity> = withContext(Dispatchers.IO) {
        try {
            val imagesDir = getSubDir("images")
            val attachmentId = UUID.randomUUID().toString()
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val targetFile = File(imagesDir, fileName)

            // Read original bitmap with downsampling if large
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream?.close()

            var sampleSize = 1
            val maxDimension = 1920
            if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val secondStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions)
            secondStream?.close()

            if (bitmap == null) {
                return@withContext Result.failure(Exception("تعذر قراءة ملف الصورة"))
            }

            // Save compressed JPEG (85% quality)
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            val fileSize = targetFile.length()
            val width = bitmap.width
            val height = bitmap.height

            val attachment = AttachmentEntity(
                attachmentId = attachmentId,
                messageId = messageId,
                chatId = chatId,
                type = "IMAGE",
                fileName = fileName,
                mimeType = "image/jpeg",
                fileSize = fileSize,
                storagePath = targetFile.absolutePath,
                thumbnailUri = targetFile.absolutePath,
                width = width,
                height = height,
                uploadProgress = 1.0f,
                uploadStatus = "SUCCESS"
            )

            Result.success(attachment)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            Result.failure(e)
        }
    }

    suspend fun saveVideoAttachment(
        uri: Uri,
        messageId: String,
        chatId: String,
        caption: String? = null
    ): Result<AttachmentEntity> = withContext(Dispatchers.IO) {
        try {
            val videosDir = getSubDir("videos")
            val attachmentId = UUID.randomUUID().toString()
            val originalName = queryFileName(uri) ?: "VID_${System.currentTimeMillis()}.mp4"
            val targetFile = File(videosDir, "VID_${System.currentTimeMillis()}_$originalName")

            // Copy video file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileSize = targetFile.length()
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                targetFile.delete()
                return@withContext Result.failure(Exception("حجم الفيديو يتجاوز الحد المسموح به (100 ميجابايت)"))
            }

            // Extract duration and thumbnail
            var durationMs = 0L
            var width = 0
            var height = 0
            var thumbnailPath: String? = null

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(targetFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L

                val frameBitmap = retriever.getFrameAtTime(1000000) // 1 second in
                if (frameBitmap != null) {
                    width = frameBitmap.width
                    height = frameBitmap.height
                    val thumbFile = File(getSubDir("thumbnails"), "THUMB_${attachmentId}.jpg")
                    FileOutputStream(thumbFile).use { out ->
                        frameBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    }
                    thumbnailPath = thumbFile.absolutePath
                }
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed extracting video metadata: ${e.message}")
            }

            val attachment = AttachmentEntity(
                attachmentId = attachmentId,
                messageId = messageId,
                chatId = chatId,
                type = "VIDEO",
                fileName = originalName,
                mimeType = context.contentResolver.getType(uri) ?: "video/mp4",
                fileSize = fileSize,
                storagePath = targetFile.absolutePath,
                thumbnailUri = thumbnailPath ?: targetFile.absolutePath,
                durationMs = durationMs,
                width = width,
                height = height,
                uploadProgress = 1.0f,
                uploadStatus = "SUCCESS"
            )

            Result.success(attachment)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video", e)
            Result.failure(e)
        }
    }

    suspend fun saveDocumentAttachment(
        uri: Uri,
        messageId: String,
        chatId: String
    ): Result<AttachmentEntity> = withContext(Dispatchers.IO) {
        try {
            val docsDir = getSubDir("documents")
            val attachmentId = UUID.randomUUID().toString()
            val originalName = queryFileName(uri) ?: "Document_${System.currentTimeMillis()}"
            val targetFile = File(docsDir, "${System.currentTimeMillis()}_$originalName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileSize = targetFile.length()
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                targetFile.delete()
                return@withContext Result.failure(Exception("حجم الملف يتجاوز الحد المسموح به (100 ميجابايت)"))
            }

            val mime = context.contentResolver.getType(uri)
                ?: getMimeTypeFromExtension(originalName)
                ?: "application/octet-stream"

            val attachment = AttachmentEntity(
                attachmentId = attachmentId,
                messageId = messageId,
                chatId = chatId,
                type = "FILE",
                fileName = originalName,
                mimeType = mime,
                fileSize = fileSize,
                storagePath = targetFile.absolutePath,
                uploadProgress = 1.0f,
                uploadStatus = "SUCCESS"
            )

            Result.success(attachment)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving document", e)
            Result.failure(e)
        }
    }

    suspend fun saveAudioAttachment(
        uri: Uri,
        messageId: String,
        chatId: String
    ): Result<AttachmentEntity> = withContext(Dispatchers.IO) {
        try {
            val audioDir = getSubDir("audio")
            val attachmentId = UUID.randomUUID().toString()
            val originalName = queryFileName(uri) ?: "Audio_${System.currentTimeMillis()}.mp3"
            val targetFile = File(audioDir, "${System.currentTimeMillis()}_$originalName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            var durationMs = 0L
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(targetFile.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get audio duration: ${e.message}")
            }

            val attachment = AttachmentEntity(
                attachmentId = attachmentId,
                messageId = messageId,
                chatId = chatId,
                type = "AUDIO",
                fileName = originalName,
                mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg",
                fileSize = targetFile.length(),
                storagePath = targetFile.absolutePath,
                durationMs = durationMs,
                uploadProgress = 1.0f,
                uploadStatus = "SUCCESS"
            )

            Result.success(attachment)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio", e)
            Result.failure(e)
        }
    }

    suspend fun saveRecordedVoice(
        sourceFile: File,
        messageId: String,
        chatId: String,
        durationMs: Long
    ): Result<AttachmentEntity> = withContext(Dispatchers.IO) {
        try {
            val voiceDir = getSubDir("voice")
            val attachmentId = UUID.randomUUID().toString()
            val fileName = "VOICE_${System.currentTimeMillis()}.amr"
            val targetFile = File(voiceDir, fileName)

            sourceFile.copyTo(targetFile, overwrite = true)

            val attachment = AttachmentEntity(
                attachmentId = attachmentId,
                messageId = messageId,
                chatId = chatId,
                type = "VOICE",
                fileName = fileName,
                mimeType = "audio/amr",
                fileSize = targetFile.length(),
                storagePath = targetFile.absolutePath,
                durationMs = durationMs,
                uploadProgress = 1.0f,
                uploadStatus = "SUCCESS"
            )

            Result.success(attachment)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving voice note", e)
            Result.failure(e)
        }
    }

    fun simulateUploadProgress(attachmentId: String): Flow<Float> = flow {
        emit(0.1f)
        delay(150)
        emit(0.35f)
        delay(200)
        emit(0.7f)
        delay(150)
        emit(1.0f)
    }

    fun getSharableFileUri(filePath: String): Uri {
        val file = File(filePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun queryFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getMimeTypeFromExtension(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        } else null
    }

    fun createCameraTempImageUri(): Pair<File, Uri> {
        val imagesDir = getSubDir("images")
        val file = File(imagesDir, "CAM_IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Pair(file, uri)
    }

    fun createCameraTempVideoUri(): Pair<File, Uri> {
        val videosDir = getSubDir("videos")
        val file = File(videosDir, "CAM_VID_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Pair(file, uri)
    }
}

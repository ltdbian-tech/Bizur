package com.bizur.android.media

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttachmentStore(private val context: Context) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    private val cacheDir = File(context.cacheDir, "attachments").apply { mkdirs() }
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val authority = "${context.packageName}.attachments"

    suspend fun fromUri(
        uri: Uri,
        explicitMimeType: String? = null,
        sizeLimitBytes: Long = MAX_ATTACHMENT_BYTES
    ): AttachmentSource = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = explicitMimeType ?: resolver.getType(uri) ?: DEFAULT_MIME
        val displayName = queryDisplayName(uri) ?: "attachment"
        resolver.openInputStream(uri)?.use { stream ->
            val bytes = readBytes(stream, sizeLimitBytes)
            val path = persist(displayName, bytes)
            val file = AttachmentFile(
                storagePath = path,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong()
            )
            AttachmentSource(file = file, bytes = bytes)
        } ?: throw IllegalStateException("Unable to open attachment uri")
    }

    suspend fun fromBytes(
        messageId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray
    ): AttachmentFile = withContext(Dispatchers.IO) {
        val safeName = if (fileName.isNotBlank()) fileName else "attachment-$messageId"
        val path = persist(safeName, data)
        AttachmentFile(
            storagePath = path,
            displayName = safeName,
            mimeType = mimeType,
            sizeBytes = data.size.toLong()
        )
    }

    fun resolve(path: String): File = File(mediaDir, path)

    suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        val target = File(mediaDir, path)
        encryptedFile(target).openFileInput().use { it.readBytes() }
    }

    suspend fun exportToCache(
        storagePath: String,
        displayName: String,
        reuseExisting: Boolean = true
    ): Uri = withContext(Dispatchers.IO) {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val safeCacheName = storagePath.replace(ILLEGAL_FILENAME_REGEX, "_")
        val safeDisplay = displayName.replace(ILLEGAL_FILENAME_REGEX, "_")
        val targetName = if (reuseExisting) safeCacheName else "${UUID.randomUUID()}_$safeDisplay"
        val target = File(cacheDir, targetName)
        if (!reuseExisting || !target.exists()) {
            val bytes = read(storagePath)
            FileOutputStream(target).use { it.write(bytes) }
        }
        target.setLastModified(System.currentTimeMillis())
        FileProvider.getUriForFile(context, authority, target)
    }

    suspend fun pruneCache(maxAgeMillis: Long = CACHE_TTL_MILLIS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun readBytes(input: InputStream, limit: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            require(total <= limit) { "Attachment exceeds allowed size (${limit / (1024 * 1024)} MB)" }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) cursor.getString(index) else null
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    private fun persist(fileName: String, bytes: ByteArray): String {
        val sanitized = fileName.replace(ILLEGAL_FILENAME_REGEX, "_")
        val finalName = "${UUID.randomUUID()}_$sanitized"
        val target = File(mediaDir, finalName)
        if (target.exists()) {
            target.delete()
        }
        encryptedFile(target).openFileOutput().use { it.write(bytes) }
        return target.name
    }

    private fun encryptedFile(target: File): EncryptedFile = EncryptedFile.Builder(
        context,
        target,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    data class AttachmentFile(
        val storagePath: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    data class AttachmentSource(
        val file: AttachmentFile,
        val bytes: ByteArray
    )

    companion object {
        private const val DEFAULT_MIME = "application/octet-stream"
        private val ILLEGAL_FILENAME_REGEX = "[^A-Za-z0-9._-]".toRegex()
        private val CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(4)
        const val MAX_ATTACHMENT_BYTES: Long = 5L * 1024L * 1024L
    }
}

package com.chase.lifeboard.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class JournalRepository(
    private val context: Context,
    private val dao: JournalDao,
) {
    val allEntries: Flow<List<JournalEntryWithPhotos>> = dao.observeAll()

    val photosDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun photoFile(fileName: String) = File(photosDir, fileName)

    fun observe(id: Long): Flow<JournalEntryWithPhotos?> = dao.observe(id)

    suspend fun get(id: Long) = dao.get(id)

    suspend fun create(day: Long): Long = dao.insert(JournalEntryEntity(day = day))

    suspend fun update(entry: JournalEntryEntity) =
        dao.update(entry.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(entry: JournalEntryEntity) {
        val photos = dao.photosFor(entry.id)
        dao.delete(entry)
        withContext(Dispatchers.IO) { photos.forEach { photoFile(it.fileName).delete() } }
    }

    /** Copies picked images into private storage so they survive the picker's temporary grant. */
    suspend fun addPhotos(entryId: Long, uris: List<Uri>) {
        var order = dao.maxPhotoOrder(entryId)
        for (uri in uris) {
            val name = "${UUID.randomUUID()}.jpg"
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        photoFile(name).outputStream().use { input.copyTo(it) }
                    } != null
                }.getOrDefault(false)
            }
            if (copied) dao.insertPhoto(JournalPhotoEntity(entryId = entryId, fileName = name, sortOrder = ++order))
        }
    }

    suspend fun removePhoto(photo: JournalPhotoEntity) {
        dao.deletePhoto(photo)
        withContext(Dispatchers.IO) { photoFile(photo.fileName).delete() }
    }
}

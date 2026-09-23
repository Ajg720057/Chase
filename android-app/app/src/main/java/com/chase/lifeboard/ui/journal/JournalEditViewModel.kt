package com.chase.lifeboard.ui.journal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.data.JournalEntryEntity
import com.chase.lifeboard.data.JournalEntryWithPhotos
import com.chase.lifeboard.data.JournalPhotoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JournalEditViewModel(
    private val app: LifeBoardApp,
    private val entryId: Long,
    private val isNew: Boolean,
) : ViewModel() {
    private val repo = app.journal
    private val writeLock = Mutex()
    private var textLoaded = false

    val title = MutableStateFlow("")
    val body = MutableStateFlow("")

    val entry: StateFlow<JournalEntryWithPhotos?> = repo.observe(entryId)
        .onEach { e ->
            if (e != null && !textLoaded) {
                title.value = e.entry.title
                body.value = e.entry.body
                textLoaded = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun mutate(transform: (JournalEntryEntity) -> JournalEntryEntity) {
        app.appScope.launch {
            writeLock.withLock {
                val current = repo.get(entryId) ?: return@withLock
                val updated = transform(current)
                if (updated != current) repo.update(updated)
            }
        }
    }

    fun setTitle(v: String) {
        title.value = v
        mutate { it.copy(title = v) }
    }

    fun setBody(v: String) {
        body.value = v
        mutate { it.copy(body = v) }
    }

    fun setMood(m: Int?) = mutate { it.copy(mood = m) }
    fun setDay(day: Long) = mutate { it.copy(day = day) }

    fun addPhotos(uris: List<Uri>) {
        app.appScope.launch { repo.addPhotos(entryId, uris) }
    }

    fun removePhoto(photo: JournalPhotoEntity) {
        app.appScope.launch { repo.removePhoto(photo) }
    }

    fun delete() {
        app.appScope.launch { writeLock.withLock { repo.get(entryId)?.let { repo.delete(it) } } }
    }

    /** Throw away a new entry that was opened and left completely empty. */
    fun onLeave() {
        if (!isNew) return
        app.appScope.launch {
            writeLock.withLock {
                val e = repo.get(entryId) ?: return@withLock
                if (e.title.isBlank() && e.body.isBlank() && e.mood == null && entry.value?.photos.isNullOrEmpty()) {
                    repo.delete(e)
                }
            }
        }
    }
}

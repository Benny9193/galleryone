package com.example.myapplication

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.Album
import com.example.myapplication.data.AppSettings
import com.example.myapplication.data.GalleryItem
import com.example.myapplication.data.MediaFilter
import com.example.myapplication.data.MediaStoreRepository
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.StartTab
import com.example.myapplication.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which system request is currently awaiting the user's confirmation. */
private enum class PendingOp { TRASH, UNTRASH, DELETE }

/** One-shot snackbar events surfaced to the UI. */
sealed interface GallerySnack {
    data class Deleted(val count: Int) : GallerySnack
    data object Restored : GallerySnack
    data class PermanentlyDeleted(val count: Int) : GallerySnack
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MediaStoreRepository(app)
    private val settingsRepository = SettingsRepository(app)

    /** Persisted settings; null until the first value is loaded from DataStore. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(theme: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setStartTab(tab: StartTab) = viewModelScope.launch { settingsRepository.setStartTab(tab) }
    fun setAutoPlayVideos(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setAutoPlayVideos(enabled) }
    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }

    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Media in the currently opened album. */
    private val _media = MutableStateFlow<List<GalleryItem>>(emptyList())
    val media: StateFlow<List<GalleryItem>> = _media.asStateFlow()

    /** All media across the device, newest first (for the timeline view). */
    private val _timeline = MutableStateFlow<List<GalleryItem>>(emptyList())
    val timeline: StateFlow<List<GalleryItem>> = _timeline.asStateFlow()

    /** Items currently in the trash (recoverable). */
    private val _trash = MutableStateFlow<List<GalleryItem>>(emptyList())
    val trash: StateFlow<List<GalleryItem>> = _trash.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** A pending system confirmation (trash or untrash) the UI must launch, if any. */
    private val _pendingIntent = MutableStateFlow<IntentSender?>(null)
    val pendingIntent: StateFlow<IntentSender?> = _pendingIntent.asStateFlow()

    /** One-shot snackbar event. */
    private val _snack = MutableStateFlow<GallerySnack?>(null)
    val snack: StateFlow<GallerySnack?> = _snack.asStateFlow()

    private var currentBucketId: Long? = null
    private var pendingOp = PendingOp.TRASH
    private var pendingCount = 0
    private var trashedUris: List<Uri> = emptyList()

    fun loadAll() {
        loadAlbums()
        loadTimeline()
        loadTrash()
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _loading.value = true
            _albums.value = repository.getAlbums(_filter.value)
            _loading.value = false
        }
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _timeline.value = repository.getMedia(_filter.value, bucketId = null)
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            _trash.value = repository.getTrashed(_filter.value)
        }
    }

    fun loadMedia(bucketId: Long) {
        currentBucketId = bucketId
        viewModelScope.launch {
            _loading.value = true
            _media.value = repository.getMedia(_filter.value, bucketId)
            _loading.value = false
        }
    }

    fun setFilter(filter: MediaFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        loadAlbums()
        loadTimeline()
        loadTrash()
        currentBucketId?.let { loadMedia(it) }
    }

    /**
     * Request trashing of one or more items. Trashing (vs. permanent delete) keeps the items
     * recoverable (~30 days) so the delete can be undone. Surfaces a system confirmation.
     */
    fun requestTrash(items: List<GalleryItem>) {
        if (items.isEmpty()) return
        val uris: List<Uri> = items.map { it.uri }
        trashedUris = uris
        pendingOp = PendingOp.TRASH
        pendingCount = items.size
        _pendingIntent.value =
            MediaStore.createTrashRequest(
                getApplication<Application>().contentResolver, uris, true
            ).intentSender
    }

    /** Restore items from the trash. Defaults to the most recently trashed batch (snackbar Undo). */
    fun requestUntrash(items: List<GalleryItem>? = null) {
        val uris = items?.map { it.uri } ?: trashedUris
        if (uris.isEmpty()) return
        pendingOp = PendingOp.UNTRASH
        _pendingIntent.value =
            MediaStore.createTrashRequest(
                getApplication<Application>().contentResolver, uris, false
            ).intentSender
    }

    /** Permanently delete items (e.g. emptying the trash). Not recoverable. */
    fun requestPermanentDelete(items: List<GalleryItem>) {
        if (items.isEmpty()) return
        pendingOp = PendingOp.DELETE
        pendingCount = items.size
        _pendingIntent.value =
            MediaStore.createDeleteRequest(
                getApplication<Application>().contentResolver, items.map { it.uri }
            ).intentSender
    }

    fun onPendingHandled(confirmed: Boolean) {
        _pendingIntent.value = null
        if (!confirmed) return
        when (pendingOp) {
            PendingOp.TRASH -> _snack.value = GallerySnack.Deleted(pendingCount)
            PendingOp.UNTRASH -> {
                _snack.value = GallerySnack.Restored
                trashedUris = emptyList()
            }
            PendingOp.DELETE -> _snack.value = GallerySnack.PermanentlyDeleted(pendingCount)
        }
        refreshAll()
    }

    fun onSnackShown() {
        _snack.value = null
    }

    private fun refreshAll() {
        currentBucketId?.let { loadMedia(it) }
        loadAlbums()
        loadTimeline()
        loadTrash()
    }
}

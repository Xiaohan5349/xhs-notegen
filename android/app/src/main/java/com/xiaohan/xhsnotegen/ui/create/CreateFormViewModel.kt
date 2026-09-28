package com.xiaohan.xhsnotegen.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.util.ExifReader
import com.xiaohan.xhsnotegen.util.ImageCleanup
import com.xiaohan.xhsnotegen.util.PhotoLocation
import com.xiaohan.xhsnotegen.util.PlaceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateFormViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_PHOTOS = 20
    }

    private val app = application as XhsNoteGenApp
    private val draftRepo = app.draftRepository
    private val styleRepo = app.stylePrefsRepository

    /** Local file:// copies (Photo Picker grants are temporary). */
    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    private val _foodInfo = MutableStateFlow(FoodInfo())
    val foodInfo: StateFlow<FoodInfo> = _foodInfo.asStateFlow()

    private val _selectedStyle = MutableStateFlow(NoteStyle.DEFAULT)
    val selectedStyle: StateFlow<NoteStyle> = _selectedStyle.asStateFlow()

    private val _photoMessage = MutableStateFlow<String?>(null)
    val photoMessage: StateFlow<String?> = _photoMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Set once the photos belong to a saved draft; until then they are ours to clean up. */
    private var saved = false

    /** Local copy → the photo it came from (needed to read the original's GPS). */
    private val originals = mutableMapOf<Uri, Uri>()

    /** Place from photo GPS, when the permission allows reading it. */
    private val _photoPlace = MutableStateFlow<Place?>(null)
    val photoPlace: StateFlow<Place?> = _photoPlace.asStateFlow()

    /** True when photos were added but their GPS can't be read without permission. */
    private val _canUnlockPhotoPlaces = MutableStateFlow(false)
    val canUnlockPhotoPlaces: StateFlow<Boolean> = _canUnlockPhotoPlaces.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedStyle.value = styleRepo.resolveStyle(NoteType.FOOD.key)
        }
    }

    val remainingPhotoSlots: Int get() = MAX_PHOTOS - _photoUris.value.size

    /** Adds picked photos to the current selection (the "+" tile used to replace it). */
    fun addPhotos(picked: List<Uri>) {
        if (picked.isEmpty()) return
        val accepted = picked.take(remainingPhotoSlots.coerceAtLeast(0))
        _photoMessage.value = if (accepted.size < picked.size) "Only $MAX_PHOTOS photos per note — kept the first ${accepted.size}." else null
        if (accepted.isEmpty()) return

        viewModelScope.launch {
            _isImporting.value = true
            try {
                val copies = withContext(Dispatchers.IO) {
                    accepted.map { ImageCleanup.copyToLocal(getApplication(), it) }
                }
                val failed = copies.count { it == null }
                if (failed > 0) _photoMessage.value = "$failed photo(s) couldn't be read and were skipped."
                accepted.zip(copies).forEach { (orig, copy) -> if (copy != null) originals[copy] = orig }
                _photoUris.value = _photoUris.value + copies.filterNotNull()
                resolvePhotoPlace()

                // EXIF comes from the originals (still readable while the grant lasts),
                // and only fills fields the user hasn't typed into.
                val exif = withContext(Dispatchers.IO) { ExifReader.aggregate(getApplication(), accepted) }
                val current = _foodInfo.value
                _foodInfo.value = current.copy(
                    location = current.location.ifBlank { exif.location.orEmpty() },
                    mealDate = current.mealDate.ifBlank { exif.captureDate.orEmpty() },
                )
            } finally {
                _isImporting.value = false
            }
        }
    }

    /**
     * Finds the place of the first photo that carries GPS. Needs photo access +
     * media location (the picker strips GPS); without them, offers to unlock.
     */
    fun resolvePhotoPlace() {
        if (_photoPlace.value != null) return
        val app = getApplication<Application>()
        if (!PhotoLocation.isGranted(app)) {
            _canUnlockPhotoPlaces.value = _photoUris.value.isNotEmpty()
            return
        }
        _canUnlockPhotoPlaces.value = false
        viewModelScope.launch {
            val latLng = withContext(Dispatchers.IO) {
                _photoUris.value.firstNotNullOfOrNull { originals[it]?.let { o -> PhotoLocation.read(app, o) } }
            } ?: return@launch
            val place = PlaceResolver.fromCoordinates(app, latLng[0], latLng[1])
                ?: Place(latitude = latLng[0], longitude = latLng[1], source = PlaceSource.GPS)
            _photoPlace.value = place
            val current = _foodInfo.value
            if (current.location.isBlank() && place.city.isNotBlank()) {
                _foodInfo.value = current.copy(location = place.city)
            }
        }
    }

    fun dismissPhotoPlaceOffer() { _canUnlockPhotoPlaces.value = false }

    fun removePhoto(uri: Uri) {
        _photoUris.value = _photoUris.value - uri
        ImageCleanup.deleteLocalFiles(listOf(uri.toString()))
        _photoMessage.value = null
    }

    /** Removes several photos at once (selection mode). */
    fun removePhotos(uris: Set<Uri>) {
        _photoUris.value = _photoUris.value.filterNot { it in uris }
        ImageCleanup.deleteLocalFiles(uris.map { it.toString() })
        _photoMessage.value = null
    }

    /** Moves a photo to the front — the first photo is the note's cover. */
    fun makeCover(uri: Uri) {
        _photoUris.value = listOf(uri) + (_photoUris.value - uri)
    }

    /** Moves a photo one step left (-1) or right (+1). */
    fun movePhoto(uri: Uri, delta: Int) {
        _photoUris.value = _photoUris.value.moved(uri, delta)
    }

    fun updateFoodInfo(info: FoodInfo) { _foodInfo.value = info }

    fun setStyle(style: NoteStyle) {
        _selectedStyle.value = style
        // Persist so the next draft defaults to this style.
        viewModelScope.launch { styleRepo.setStyleForType(NoteType.FOOD.key, style) }
    }

    /** Returns the new draft id, or null if a save is already in flight. */
    suspend fun saveDraftSuspend(): Long? {
        if (_isSaving.value) return null // double-tap guard
        if (!_foodInfo.value.isValid()) throw IllegalStateException("Dish and restaurant name required")
        val count = _photoUris.value.size
        if (count < 1 || count > MAX_PHOTOS) throw IllegalStateException("Select 1-$MAX_PHOTOS photos")

        _isSaving.value = true
        try {
            val draft = NoteDraft(
                type = NoteType.FOOD,
                status = NoteStatus.DRAFT,
                photoUris = _photoUris.value.map { it.toString() },
                styleLabel = _selectedStyle.value.key,
                foodInfo = _foodInfo.value.copy(place = _photoPlace.value ?: Place()),
            )
            val id = draftRepo.insert(draft).also { saved = true }
            // No photo GPS: work out the place from the text, without delaying generation.
            if (_photoPlace.value?.isKnown != true) {
                val app = getApplication<Application>()
                val info = draft.foodInfo
                this.app.applicationScope.launch {
                    PlaceResolver.forNote(app, info)?.let { draftRepo.setPlace(id, it) }
                }
            }
            return id
        } finally {
            _isSaving.value = false
        }
    }

    override fun onCleared() {
        // Abandoned form: the copied photos belong to no draft, so nothing else would ever delete them.
        if (!saved) ImageCleanup.deleteLocalFiles(_photoUris.value.map { it.toString() })
        super.onCleared()
    }
}

/** Returns a copy with [item] moved by [delta] positions (clamped to the list bounds). */
internal fun <T> List<T>.moved(item: T, delta: Int): List<T> {
    val from = indexOf(item)
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, size - 1)
    if (to == from) return this
    return toMutableList().apply { removeAt(from); add(to, item) }
}

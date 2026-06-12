package com.xiaohan.xhsnotegen.ui.create

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.util.ExifReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateFormViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XhsNoteGenApp
    private val draftRepo = app.draftRepository
    private val styleRepo = app.stylePrefsRepository

    private val _photoUris = MutableStateFlow<List<Uri>>(emptyList())
    val photoUris: StateFlow<List<Uri>> = _photoUris.asStateFlow()

    private val _foodInfo = MutableStateFlow(FoodInfo())
    val foodInfo: StateFlow<FoodInfo> = _foodInfo.asStateFlow()

    private val _selectedStyle = MutableStateFlow(NoteStyle.DEFAULT)
    val selectedStyle: StateFlow<NoteStyle> = _selectedStyle.asStateFlow()

    private val _photoCountError = MutableStateFlow<String?>(null)
    val photoCountError: StateFlow<String?> = _photoCountError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            val resolved = styleRepo.resolveStyle(NoteType.FOOD.key)
            _selectedStyle.value = resolved
        }
    }

    fun setPhotos(uris: List<Uri>) {
        _photoUris.value = uris
        _photoCountError.value = when {
            uris.isEmpty() -> null
            uris.size < 5 -> "Select at least 5 photos (${uris.size} selected)"
            uris.size > 20 -> "Maximum 20 photos allowed"
            else -> null
        }

        if (uris.isNotEmpty()) {
            viewModelScope.launch {
                val exif = withContext(Dispatchers.IO) {
                    ExifReader.aggregate(getApplication(), uris)
                }
                val current = _foodInfo.value
                _foodInfo.value = current.copy(
                    location = exif.location ?: current.location,
                    mealDate = exif.captureDate ?: current.mealDate,
                )
            }
        }
    }

    fun updateFoodInfo(info: FoodInfo) { _foodInfo.value = info }
    fun setStyle(style: NoteStyle) { _selectedStyle.value = style }

    suspend fun saveDraftSuspend(): Long {
        if (!_foodInfo.value.isValid()) throw IllegalStateException("Dish and restaurant name required")
        val count = _photoUris.value.size
        if (count < 5 || count > 20) throw IllegalStateException("Select 5-20 photos")

        val draft = NoteDraft(
            type = NoteType.FOOD,
            status = NoteStatus.DRAFT,
            photoUris = _photoUris.value.map { it.toString() },
            styleLabel = _selectedStyle.value.key,
            foodInfo = _foodInfo.value,
        )
        return draftRepo.insert(draft)
    }
}

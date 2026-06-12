package com.xiaohan.xhsnotegen.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft: StateFlow<NoteDraft?> = _draft.asStateFlow()

    private val _selectedVariantIndex = MutableStateFlow(0)
    val selectedVariantIndex: StateFlow<Int> = _selectedVariantIndex.asStateFlow()

    fun load(draftId: Long) {
        viewModelScope.launch {
            val d = repo.getById(draftId)
            _draft.value = d
            _selectedVariantIndex.value = d?.selectedVariantIndex ?: 0
        }
    }

    fun selectVariant(index: Int) {
        _selectedVariantIndex.value = index
        _draft.value?.let { draft ->
            val updated = draft.copy(selectedVariantIndex = index)
            _draft.value = updated
            viewModelScope.launch { repo.update(updated) }
        }
    }

    fun updateVariantTitle(text: String) {
        val index = _selectedVariantIndex.value
        _draft.value?.let { draft ->
            val variants = draft.variants.toMutableList()
            if (index in variants.indices) variants[index] = variants[index].copy(title = text)
            _draft.value = draft.copy(variants = variants)
        }
    }

    fun updateVariantBody(text: String) {
        val index = _selectedVariantIndex.value
        _draft.value?.let { draft ->
            val variants = draft.variants.toMutableList()
            if (index in variants.indices) variants[index] = variants[index].copy(body = text)
            _draft.value = draft.copy(variants = variants)
        }
    }

    fun updateVariantHashtags(hashtags: List<String>) {
        val index = _selectedVariantIndex.value
        _draft.value?.let { draft ->
            val variants = draft.variants.toMutableList()
            if (index in variants.indices) variants[index] = variants[index].copy(hashtags = hashtags)
            _draft.value = draft.copy(variants = variants)
        }
    }

    fun togglePublishPhoto(index: Int) {
        _draft.value?.let { draft ->
            val uri = draft.photoUris.getOrNull(index) ?: return
            val selected = draft.selectedPublishPhotoUris.toMutableList()
            if (uri in selected) selected.remove(uri) else selected.add(uri)
            _draft.value = draft.copy(selectedPublishPhotoUris = selected)
        }
    }

    fun saveChanges() {
        _draft.value?.let { draft ->
            viewModelScope.launch {
                repo.update(draft.copy(status = NoteStatus.REVIEWED))
            }
        }
    }
}

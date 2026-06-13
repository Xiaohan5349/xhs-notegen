package com.xiaohan.xhsnotegen.ui.review

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.ui.generate.FoodPrompts
import com.xiaohan.xhsnotegen.ui.generate.GeminiClient
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft: StateFlow<NoteDraft?> = _draft.asStateFlow()

    private val _selectedVariantIndex = MutableStateFlow(0)
    val selectedVariantIndex: StateFlow<Int> = _selectedVariantIndex.asStateFlow()

    private val _isRegenerating = MutableStateFlow(false)
    val isRegenerating: StateFlow<Boolean> = _isRegenerating.asStateFlow()

    private val _regenerateError = MutableStateFlow<String?>(null)
    val regenerateError: StateFlow<String?> = _regenerateError.asStateFlow()

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

    fun markShared() {
        _draft.value?.let { draft ->
            _draft.value = draft.copy(status = NoteStatus.SHARED)
            viewModelScope.launch {
                repo.update(draft.copy(status = NoteStatus.SHARED))
            }
        }
    }

    fun regenerateAllStyles() {
        val draft = _draft.value ?: return
        _isRegenerating.value = true
        _regenerateError.value = null

        viewModelScope.launch {
            try {
                // Compress images
                val photos = draft.photoUris.map { Uri.parse(it) }
                val compressedImages = withContext(Dispatchers.IO) {
                    photos.map { uri ->
                        val result = ImageCompressor.compress(getApplication(), uri)
                        if (!result.success) throw IllegalStateException("Compress failed: ${result.error}")
                        result.base64
                    }
                }

                // Generate all 4 styles in parallel
                val results = NoteStyle.entries.map { style ->
                    async(Dispatchers.IO) {
                        style to runCatching {
                            val prompt = FoodPrompts.buildUserPrompt(style, draft.foodInfo)
                            val generated = GeminiClient.generateFoodNote(
                                getApplication(), FoodPrompts.SYSTEM_PROMPT, prompt, compressedImages
                            )
                            generated.firstOrNull()?.let {
                                NoteVariant(it.styleLabel, it.title, it.body, it.hashtags, it.warnings)
                            }
                        }
                    }
                }.awaitAll()

                val errors = results.mapNotNull { (style, result) ->
                    result.exceptionOrNull()?.let { "${style.displayName}: ${it.message}" }
                }
                if (errors.isNotEmpty() && results.none { it.second.isSuccess }) {
                    throw IllegalStateException(errors.joinToString("\n"))
                }
                val variants = results.mapNotNull { it.second.getOrNull() }

                repo.saveGeneratedVariants(draft.id, variants)
                _draft.value = draft.copy(variants = variants, selectedVariantIndex = 0)
                _selectedVariantIndex.value = 0
            } catch (e: Exception) {
                _regenerateError.value = e.message ?: "Regenerate failed"
            } finally {
                _isRegenerating.value = false
            }
        }
    }
}

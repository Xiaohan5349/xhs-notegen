package com.xiaohan.xhsnotegen.ui.generate

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.data.remote.RetrofitClient
import com.xiaohan.xhsnotegen.data.remote.dto.*
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GenerationState(
    val isCompressing: Boolean = true,
    val isGenerating: Boolean = false,
    val progress: String = "Compressing photos...",
    val error: String? = null,
    val variants: List<NoteVariant> = emptyList(),
    val isComplete: Boolean = false,
)

class GenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XhsNoteGenApp
    private val draftRepo = app.draftRepository

    private val _state = MutableStateFlow(GenerationState())
    val state: StateFlow<GenerationState> = _state.asStateFlow()

    fun generate(draftId: Long) {
        viewModelScope.launch {
            try {
                val draft = draftRepo.getById(draftId)
                    ?: throw IllegalStateException("Draft not found")

                // Step 1: Compress images
                _state.value = GenerationState(isCompressing = true, progress = "Compressing photos...")
                val photos = draft.photoUris.map { Uri.parse(it) }
                val compressedImages = withContext(Dispatchers.IO) {
                    photos.mapIndexed { i, uri ->
                        _state.value = _state.value.copy(
                            progress = "Compressing photo ${i + 1}/${photos.size}..."
                        )
                        val result = ImageCompressor.compress(getApplication(), uri)
                        if (!result.success) {
                            throw IllegalStateException(
                                "Failed to compress photo ${i + 1}: ${result.error}"
                            )
                        }
                        result.base64
                    }
                }

                // Step 2: Call backend
                _state.value = GenerationState(
                    isCompressing = false, isGenerating = true,
                    progress = "Generating note variants...",
                )

                val request = GenerateRequestDto(
                    noteType = "food",
                    style = draft.styleLabel,
                    metadata = FoodMetadataDto(
                        dishName = draft.foodInfo.dishName,
                        restaurantName = draft.foodInfo.restaurantName,
                        location = draft.foodInfo.location.ifBlank { null },
                        mealDate = draft.foodInfo.mealDate.ifBlank { null },
                        tasteNotes = draft.foodInfo.tasteNotes.ifBlank { null },
                        priceOrRating = draft.foodInfo.priceOrRating.ifBlank { null },
                        vibeNotes = draft.foodInfo.vibeNotes.ifBlank { null },
                        personalNotes = draft.foodInfo.personalNotes.ifBlank { null },
                    ),
                    images = compressedImages,
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.getApi().generate(request)
                }
                val variants = response.toDomain()

                // Step 3: Save results
                draftRepo.saveGeneratedVariants(draftId, variants)

                _state.value = GenerationState(
                    isCompressing = false, isGenerating = false,
                    progress = "Done!", variants = variants,
                    isComplete = true, error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCompressing = false, isGenerating = false,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }
}

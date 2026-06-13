package com.xiaohan.xhsnotegen.ui.generate

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

                // Check API key
                val apiKey = GeminiClient.getApiKey(getApplication())
                if (apiKey.isNullOrBlank()) {
                    throw IllegalStateException(
                        "Gemini API key not set. Get a free key at https://aistudio.google.com/apikey"
                    )
                }

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

                // Step 2: Generate 3 variants in parallel via Gemini
                _state.value = GenerationState(
                    isCompressing = false, isGenerating = true,
                    progress = "Generating note variants...",
                )

                val preferredStyle = NoteStyle.fromKey(draft.styleLabel)
                val otherStyles = NoteStyle.entries
                    .filter { it != preferredStyle }
                    .take(3)  // all 4 styles

                val userPrompt = FoodPrompts.buildUserPrompt(preferredStyle, draft.foodInfo)

                // Parallel Gemini calls — 3 styles
                val results = listOf(preferredStyle) + otherStyles
                val variants = results.map { style ->
                    viewModelScope.async(Dispatchers.IO) {
                        runCatching {
                            val prompt = FoodPrompts.buildUserPrompt(style, draft.foodInfo)
                            val generated = GeminiClient.generateFoodNote(
                                getApplication(),
                                FoodPrompts.SYSTEM_PROMPT,
                                prompt,
                                compressedImages,
                            )
                            val v = generated.firstOrNull()
                            if (v != null) {
                                NoteVariant(
                                    styleLabel = v.styleLabel,
                                    title = v.title,
                                    body = v.body,
                                    hashtags = v.hashtags,
                                    warnings = v.warnings,
                                )
                            } else null
                        }
                    }
                }.awaitAll().mapNotNull { it.getOrNull() }

                if (variants.isEmpty()) {
                    throw IllegalStateException("All Gemini calls failed")
                }

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

package com.xiaohan.xhsnotegen.ui.generate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GenerationState(
    val phase: NoteGenerator.Phase = NoteGenerator.Phase.PREPARING_PHOTOS,
    val photoUris: List<String> = emptyList(),
    val error: String? = null,
    val isComplete: Boolean = false,
)

class GenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val draftRepo = (application as XhsNoteGenApp).draftRepository

    private val _state = MutableStateFlow(GenerationState())
    val state: StateFlow<GenerationState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Idempotent: the screen calls this from a LaunchedEffect, which re-runs
     * after every configuration change (rotation). Without this guard each
     * rotation fired another full, billed Gemini request.
     */
    fun start(draftId: Long) {
        if (job?.isActive == true || _state.value.isComplete || _state.value.error != null) return
        run(draftId)
    }

    fun retry(draftId: Long) {
        if (job?.isActive == true) return
        _state.update { it.copy(error = null, phase = NoteGenerator.Phase.PREPARING_PHOTOS) }
        run(draftId)
    }

    private fun run(draftId: Long) {
        job = viewModelScope.launch {
            try {
                val draft = draftRepo.getById(draftId)
                    ?: throw IllegalStateException(tr("This note no longer exists.", "这篇笔记已不存在。"))
                _state.update { it.copy(photoUris = draft.photoUris) }

                val styles = NoteStyle.orderedFrom(draft.preferredStyle)
                val variants = NoteGenerator.generate(getApplication(), draftRepo, draft, styles) { phase ->
                    _state.update { it.copy(phase = phase) }
                }

                // Re-read: the draft may have changed while the request was in flight.
                val latest = draftRepo.getById(draftId) ?: draft
                draftRepo.update(
                    latest.copy(
                        variants = variants,
                        selectedVariantIndex = 0,
                        status = if (latest.status == NoteStatus.SHARED) NoteStatus.SHARED else NoteStatus.GENERATED,
                    )
                )
                _state.update { it.copy(isComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: tr("Something went wrong", "出错了")) }
            }
        }
    }
}

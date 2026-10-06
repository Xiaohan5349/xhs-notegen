package com.xiaohan.xhsnotegen.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.data.json.normalizeHashtags
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.i18n.tr
import com.xiaohan.xhsnotegen.ui.create.moved
import com.xiaohan.xhsnotegen.ui.generate.NoteGenerator
import com.xiaohan.xhsnotegen.ui.publish.XiaohongshuSharePublisher
import com.xiaohan.xhsnotegen.ui.publish.XiaohongshuSharePublisher.PublishResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ReviewEvent {
    data class Message(val text: String) : ReviewEvent
    data class Published(val link: String) : ReviewEvent
    data class HandedOff(val reason: String?, val openedXhs: Boolean) : ReviewEvent
    data class NeedsLogin(val expired: Boolean) : ReviewEvent
}

/** What the AI is currently doing on this screen, if anything. */
enum class AiTask { NONE, REGENERATE_ALL, REWRITE_ONE }

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XhsNoteGenApp
    private val repo = app.draftRepository

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft: StateFlow<NoteDraft?> = _draft.asStateFlow()

    private val _aiTask = MutableStateFlow(AiTask.NONE)
    val aiTask: StateFlow<AiTask> = _aiTask.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _events = Channel<ReviewEvent>(Channel.BUFFERED)
    val events: Flow<ReviewEvent> = _events.receiveAsFlow()

    private var loadedId: Long? = null
    private var saveJob: Job? = null
    private var hasPendingSave = false
    private val writeLock = Mutex()

    fun load(draftId: Long) {
        if (loadedId == draftId) return // survives rotation without clobbering in-memory edits
        loadedId = draftId
        viewModelScope.launch {
            val d = repo.getById(draftId) ?: return@launch
            // Make "publish all photos" explicit so the UI and the publisher agree.
            _draft.value = d.copy(
                selectedPublishPhotoUris = d.publishPhotoUris,
                selectedVariantIndex = d.selectedVariantIndex.coerceIn(0, (d.variants.size - 1).coerceAtLeast(0)),
            )
        }
    }

    // ---- Editing ----
    //
    // Every change goes through mutate(): the in-memory draft is the single
    // source of truth, and persistence always writes the latest copy. (Before,
    // Save wrote a status the in-memory copy never saw, so the next keystroke
    // silently reverted "Ready" back to "Generated".)

    private fun mutate(debounce: Boolean = true, transform: (NoteDraft) -> NoteDraft) {
        val current = _draft.value ?: return
        _draft.value = transform(current)
        if (debounce) schedulePersist() else persistNow()
    }

    private fun schedulePersist() {
        hasPendingSave = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            persistNow()
        }
    }

    private fun persistNow() {
        saveJob?.cancel()
        hasPendingSave = false
        if (_draft.value == null) return
        // Application scope: a write must not be cancelled just because the user
        // left the screen. Serialized, and always writes the *latest* state, so
        // two quick writes can never land out of order.
        app.applicationScope.launch {
            writeLock.withLock { _draft.value?.let { repo.update(it) } }
        }
    }

    fun selectVariant(index: Int) = mutate(debounce = false) {
        it.copy(selectedVariantIndex = index.coerceIn(0, (it.variants.size - 1).coerceAtLeast(0)))
    }

    private fun updateSelectedVariant(transform: (NoteVariant) -> NoteVariant) =
        mutate { d ->
            val i = d.selectedVariantIndex
            if (i !in d.variants.indices) d
            else d.copy(variants = d.variants.toMutableList().also { it[i] = transform(it[i]) })
        }

    fun updateTitle(text: String) = updateSelectedVariant { it.copy(title = text) }
    fun updateBody(text: String) = updateSelectedVariant { it.copy(body = text) }

    fun addHashtags(raw: String) = updateSelectedVariant { v ->
        v.copy(hashtags = normalizeHashtags(v.hashtags + raw))
    }

    fun removeHashtag(tag: String) = updateSelectedVariant { v -> v.copy(hashtags = v.hashtags - tag) }

    /** Toggles a photo in or out of the post. Selection order = publish order (first is the cover). */
    fun togglePublishPhoto(uri: String) {
        val d = _draft.value ?: return
        val selected = d.selectedPublishPhotoUris
        if (uri in selected && selected.size == 1) {
            _events.trySend(ReviewEvent.Message(tr("A post needs at least one photo", "笔记至少需要一张照片")))
            return
        }
        mutate { it.copy(selectedPublishPhotoUris = if (uri in selected) selected - uri else selected + uri) }
    }

    /** Makes [uri] the cover: first in the post (adding it to the post if it was left out). */
    fun setCover(uri: String) = mutate { d ->
        d.copy(selectedPublishPhotoUris = listOf(uri) + (d.selectedPublishPhotoUris - uri))
    }

    /** Moves an included photo earlier (-1) or later (+1) in the post. */
    fun movePublishPhoto(uri: String, delta: Int) = mutate { d ->
        d.copy(selectedPublishPhotoUris = d.selectedPublishPhotoUris.moved(uri, delta))
    }

    /** Includes every photo, keeping the current order and appending the rest. */
    fun includeAllPhotos() = mutate { d ->
        d.copy(selectedPublishPhotoUris = d.selectedPublishPhotoUris + (d.photoUris - d.selectedPublishPhotoUris.toSet()))
    }

    fun setRating(value: Int) = mutate(debounce = false) { it.copy(rating = value.coerceIn(0, 5)) }

    /** Place set by hand on this note; auto-organize won't overwrite it. */
    fun setPlace(place: com.xiaohan.xhsnotegen.domain.Place) {
        mutate(debounce = false) { it.copy(foodInfo = it.foodInfo.copy(place = place)) }
        val id = _draft.value?.id ?: return
        app.applicationScope.launch { repo.setPlace(id, place) }
    }

    fun saveChanges() {
        mutate(debounce = false) {
            if (it.status == NoteStatus.SHARED) it else it.copy(status = NoteStatus.REVIEWED)
        }
        _events.trySend(ReviewEvent.Message(tr("Saved — marked as ready to post", "已保存，标为待发布")))
    }

    fun markShared() = mutate(debounce = false) { it.copy(status = NoteStatus.SHARED) }

    // ---- AI ----

    /** The language the next rewrite is written in; the notes already written are not changed. */
    fun setLanguage(l: com.xiaohan.xhsnotegen.domain.PromptLanguage) = mutate(debounce = false) { it.copy(language = l) }

    /** Rewrites every style. Replaces all variants, including manual edits. */
    fun regenerateAll() {
        val d = _draft.value ?: return
        runAi(AiTask.REGENERATE_ALL, NoteStyle.orderedFrom(d.preferredStyle)) { current, variants ->
            current.copy(variants = variants, selectedVariantIndex = 0)
        }
    }

    /** Rewrites only the style currently shown. */
    fun rewriteCurrent() {
        val d = _draft.value ?: return
        val index = d.selectedVariantIndex
        val style = d.selectedVariant?.let { NoteStyle.fromLabel(it.styleLabel) } ?: return
        runAi(AiTask.REWRITE_ONE, listOf(style)) { current, variants ->
            val fresh = variants.firstOrNull() ?: return@runAi current
            if (index !in current.variants.indices) current
            else current.copy(variants = current.variants.toMutableList().also { it[index] = fresh })
        }
    }

    private fun runAi(
        task: AiTask,
        styles: List<NoteStyle>,
        merge: (NoteDraft, List<NoteVariant>) -> NoteDraft,
    ) {
        if (_aiTask.value != AiTask.NONE) return
        val snapshot = _draft.value ?: return
        _aiTask.value = task
        viewModelScope.launch {
            try {
                val variants = NoteGenerator.generate(getApplication(), repo, snapshot, styles)
                // Merge into the *current* draft so photo toggles made meanwhile survive.
                mutate(debounce = false) { current ->
                    val merged = merge(current, variants)
                    if (merged.status == NoteStatus.DRAFT) merged.copy(status = NoteStatus.GENERATED) else merged
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend(ReviewEvent.Message(e.message ?: tr("Couldn't generate", "生成失败")))
            } finally {
                _aiTask.value = AiTask.NONE
            }
        }
    }

    // ---- Publishing ----
    //
    // Runs in viewModelScope rather than the screen's composition scope, so
    // rotating the phone mid-upload no longer cancels a half-finished publish.

    fun publish() = runPublish { XiaohongshuSharePublisher.publish(getApplication(), it) }

    fun publishManually() = runPublish { XiaohongshuSharePublisher.handoff(getApplication(), it) }

    private fun runPublish(block: suspend (NoteDraft) -> PublishResult) {
        if (_isPublishing.value) return
        val d = _draft.value ?: return
        if (hasPendingSave) persistNow()
        _isPublishing.value = true
        viewModelScope.launch {
            try {
                when (val result = block(d)) {
                    is PublishResult.Success -> {
                        markShared()
                        _events.send(ReviewEvent.Published(result.shareLink))
                    }
                    is PublishResult.Handoff -> _events.send(ReviewEvent.HandedOff(result.reason, result.openedXhs))
                    is PublishResult.NeedsLogin -> _events.send(ReviewEvent.NeedsLogin(result.expired))
                    is PublishResult.Error -> _events.send(ReviewEvent.Message(result.message))
                }
            } finally {
                _isPublishing.value = false
            }
        }
    }

    override fun onCleared() {
        // The debounce window may still hold the user's last keystrokes.
        if (hasPendingSave) persistNow()
        super.onCleared()
    }
}

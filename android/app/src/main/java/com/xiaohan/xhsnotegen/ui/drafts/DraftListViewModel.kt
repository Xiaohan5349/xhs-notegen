package com.xiaohan.xhsnotegen.ui.drafts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.data.json.ExportData
import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.util.ImageCleanup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DraftFilter(val label: String) {
    ALL("All"), DRAFTS("Drafts"), READY("Ready"), SHARED("Posted");

    fun matches(status: NoteStatus): Boolean = when (this) {
        ALL -> true
        DRAFTS -> status == NoteStatus.DRAFT || status == NoteStatus.GENERATED
        READY -> status == NoteStatus.REVIEWED
        SHARED -> status == NoteStatus.SHARED
    }
}

data class DraftListState(
    val drafts: List<NoteDraft> = emptyList(),
    val counts: Map<DraftFilter, Int> = emptyMap(),
    val filter: DraftFilter = DraftFilter.ALL,
    val loaded: Boolean = false,
)

class DraftListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    private val _filter = MutableStateFlow(DraftFilter.ALL)

    val state: StateFlow<DraftListState> = combine(repo.getAllFlow(), _filter) { all, filter ->
        DraftListState(
            drafts = all.filter { filter.matches(it.status) },
            counts = DraftFilter.entries.associateWith { f -> all.count { f.matches(it.status) } },
            filter = filter,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DraftListState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setFilter(filter: DraftFilter) {
        _filter.value = filter
    }

    fun deleteDraft(draft: NoteDraft) {
        viewModelScope.launch { repo.deleteById(draft.id) }
    }

    // ---- Export ----

    fun exportToUri(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val all = repo.getAll()
                withContext(Dispatchers.IO) {
                    val json = JsonCodec.toJson(ExportData(drafts = all))
                    getApplication<Application>().contentResolver
                        .openOutputStream(targetUri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("Can't write to that file")
                }
                _messages.send("Exported ${all.size} notes (text only — photos stay on this phone)")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Export failed")
            }
        }
    }

    // ---- Import ----

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val drafts = withContext(Dispatchers.IO) {
                    val json = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Cannot read file")
                    JsonCodec.parseImport(json).drafts.orEmpty().filterNotNull()
                        .map { it.toDomain() }
                        .map { withOwnPhotoCopies(it) }
                }
                if (drafts.isEmpty()) {
                    _messages.send("No notes found in that file")
                    return@launch
                }
                repo.insertAll(drafts)
                _messages.send("Imported ${drafts.size} notes")
            } catch (e: Exception) {
                _messages.send("Import failed: ${e.message ?: "invalid file"}")
            }
        }
    }

    /**
     * Imported drafts get their own photo copies. Re-importing a backup on
     * the same phone used to make two drafts share files, so deleting either
     * one deleted the other's photos. References to photos that no longer
     * exist are dropped rather than shown as broken images.
     */
    private fun withOwnPhotoCopies(draft: NoteDraft): NoteDraft {
        val mapping = draft.photoUris
            .filter { ImageCleanup.localFileExists(it) }
            .associateWith { ImageCleanup.copyToLocal(getApplication(), Uri.parse(it))?.toString() }
            .filterValues { it != null }
            .mapValues { it.value!! }
        return draft.copy(
            photoUris = draft.photoUris.mapNotNull { mapping[it] },
            selectedPublishPhotoUris = draft.selectedPublishPhotoUris.mapNotNull { mapping[it] },
        )
    }
}

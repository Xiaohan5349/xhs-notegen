package com.xiaohan.xhsnotegen.ui.drafts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DraftListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    // 0 = All, 1 = Drafts (DRAFT + GENERATED), 2 = Ready (REVIEWED), 3 = Shared (SHARED)
    private val _filterTab = MutableStateFlow(0)
    val filterTab: StateFlow<Int> = _filterTab.asStateFlow()

    val filterStatus: StateFlow<NoteStatus?> = _filterTab.map { tab ->
        when (tab) {
            1 -> NoteStatus.DRAFT   // used as key — filter logic below catches DRAFT+GENERATED
            2 -> NoteStatus.REVIEWED
            3 -> NoteStatus.SHARED
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val drafts: StateFlow<List<NoteDraft>> = combine(
        repo.getAllFlow(),
        _filterTab,
    ) { all, tab ->
        when (tab) {
            1 -> all.filter { it.status == NoteStatus.DRAFT || it.status == NoteStatus.GENERATED }
            2 -> all.filter { it.status == NoteStatus.REVIEWED }
            3 -> all.filter { it.status == NoteStatus.SHARED }
            else -> all
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftCount: StateFlow<Int> = drafts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setFilterTab(tab: Int) {
        _filterTab.value = tab
    }

    fun deleteDraft(draft: NoteDraft) {
        viewModelScope.launch { repo.deleteById(draft.id) }
    }

    // ---- Export ----

    fun exportToUri(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val all = repo.getAll()
                val json = Gson().toJson(ExportData(drafts = all))
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(targetUri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                }
                _exportResult.emit(ExportResult.Success(all.size))
            } catch (e: Exception) {
                _exportResult.emit(ExportResult.Error(e.message ?: "Export failed"))
            }
        }
    }

    private val _exportResult = MutableSharedFlow<ExportResult>()
    val exportResult: SharedFlow<ExportResult> = _exportResult.asSharedFlow()

    // ---- Import ----

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
                } ?: run {
                    _importResult.emit(ImportResult.Error("Cannot read file"))
                    return@launch
                }

                val exportData = Gson().fromJson(json, ExportData::class.java)
                    ?: run {
                        _importResult.emit(ImportResult.Error("Invalid file format"))
                        return@launch
                    }

                if (exportData.drafts.isEmpty()) {
                    _importResult.emit(ImportResult.Error("No drafts found in file"))
                    return@launch
                }

                repo.insertAll(exportData.drafts)
                _importResult.emit(ImportResult.Success(exportData.drafts.size))
            } catch (e: Exception) {
                _importResult.emit(ImportResult.Error(e.message ?: "Import failed"))
            }
        }
    }

    private val _importResult = MutableSharedFlow<ImportResult>()
    val importResult: SharedFlow<ImportResult> = _importResult.asSharedFlow()
}

data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val drafts: List<NoteDraft> = emptyList(),
)

sealed class ImportResult {
    data class Success(val count: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

sealed class ExportResult {
    data class Success(val count: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

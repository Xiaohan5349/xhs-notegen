package com.xiaohan.xhsnotegen.ui.drafts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohan.xhsnotegen.XhsNoteGenApp
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DraftListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as XhsNoteGenApp).draftRepository

    val drafts: StateFlow<List<NoteDraft>> = repo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftCount: StateFlow<Int> = drafts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun deleteDraft(draft: NoteDraft) {
        viewModelScope.launch { repo.delete(draft) }
    }

    fun getStatusDisplayName(status: NoteStatus): String = when (status) {
        NoteStatus.DRAFT -> "Draft"
        NoteStatus.GENERATED -> "Generated"
        NoteStatus.REVIEWED -> "Ready"
        NoteStatus.SHARED -> "Shared"
    }
}

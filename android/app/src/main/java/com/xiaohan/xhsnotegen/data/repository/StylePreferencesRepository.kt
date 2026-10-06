package com.xiaohan.xhsnotegen.data.repository

import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.entity.StylePreferenceEntity
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.domain.NoteStyle

class StylePreferencesRepository(private val db: AppDatabase) {

    private val dao = db.stylePreferenceDao()

    suspend fun resolveStyle(noteType: String): NoteStyle {
        val typePref = dao.getByNoteType(noteType)?.toDomain()
        val globalPref = dao.getByNoteType("all")?.toDomain()
        return NoteStyle.resolve(typePref, globalPref)
    }

    suspend fun setStyleForType(noteType: String, style: NoteStyle) {
        val existing = dao.getByNoteType(noteType)
        if (existing != null) {
            dao.update(existing.copy(preferredStyle = style.key))
        } else {
            dao.insert(StylePreferenceEntity(noteType = noteType, preferredStyle = style.key))
        }
    }
}

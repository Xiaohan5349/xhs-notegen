package com.xiaohan.xhsnotegen.data.repository

import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.entity.StylePreferenceEntity
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.domain.NoteStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StylePreferencesRepository(private val db: AppDatabase) {

    private val dao = db.stylePreferenceDao()

    suspend fun getGlobalStyle(): NoteStyle? =
        dao.getByNoteType("all")?.toDomain()

    suspend fun getStyleForType(noteType: String): NoteStyle? =
        dao.getByNoteType(noteType)?.toDomain()

    suspend fun resolveStyle(noteType: String): NoteStyle {
        val typePref = getStyleForType(noteType)
        val globalPref = getGlobalStyle()
        return NoteStyle.resolve(typePref, globalPref)
    }

    suspend fun setGlobalStyle(style: NoteStyle) {
        val existing = dao.getByNoteType("all")
        if (existing != null) {
            dao.update(existing.copy(preferredStyle = style.key))
        } else {
            dao.insert(StylePreferenceEntity(noteType = "all", preferredStyle = style.key))
        }
    }

    suspend fun setStyleForType(noteType: String, style: NoteStyle) {
        val existing = dao.getByNoteType(noteType)
        if (existing != null) {
            dao.update(existing.copy(preferredStyle = style.key))
        } else {
            dao.insert(StylePreferenceEntity(noteType = noteType, preferredStyle = style.key))
        }
    }

    fun getAllFlow(): Flow<List<StylePreferenceEntity>> = dao.getAllFlow()
}

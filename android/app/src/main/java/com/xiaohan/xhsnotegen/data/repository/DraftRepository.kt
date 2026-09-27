package com.xiaohan.xhsnotegen.data.repository

import androidx.room.withTransaction
import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.entity.NoteDraftEntity
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.data.local.toEntity
import com.xiaohan.xhsnotegen.domain.*
import com.xiaohan.xhsnotegen.util.ImageCleanup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DraftRepository(private val db: AppDatabase) {

    private val draftDao = db.noteDraftDao()
    private val foodDao = db.foodInfoDao()

    /** Maps a row to the domain model; a row with corrupted JSON is skipped, not fatal. */
    private suspend fun NoteDraftEntity.toDomainOrNull(): NoteDraft? = try {
        toDomain(foodDao.getByDraftId(id)?.toDomain())
    } catch (e: Exception) {
        null
    }

    fun getAllFlow(): Flow<List<NoteDraft>> = draftDao.getAllFlow().map { entities ->
        entities.mapNotNull { it.toDomainOrNull() }
    }

    suspend fun getById(id: Long): NoteDraft? = draftDao.getById(id)?.toDomainOrNull()

    suspend fun getAll(): List<NoteDraft> = draftDao.getAll().mapNotNull { it.toDomainOrNull() }

    suspend fun insert(draft: NoteDraft): Long = db.withTransaction {
        val draftId = draftDao.insert(draft.toEntity())
        foodDao.insert(draft.foodInfo.toEntity(draftId))
        draftId
    }

    suspend fun update(draft: NoteDraft) = db.withTransaction {
        draftDao.update(draft.copy(updatedAt = System.currentTimeMillis()).toEntity())
        foodDao.deleteByDraftId(draft.id)
        foodDao.insert(draft.foodInfo.toEntity(draft.id))
    }

    suspend fun deleteById(id: Long) {
        val draft = getById(id)
        draftDao.deleteById(id)
        // Draft deletion must also remove the local photo copies in filesDir/images/,
        // otherwise deleted drafts leave unmanaged (and private) image files behind.
        draft?.let { ImageCleanup.deleteLocalFiles(it.photoUris) }
    }

    /** Import: all-or-nothing, and every draft gets a fresh auto-generated id. */
    suspend fun insertAll(drafts: List<NoteDraft>) = db.withTransaction {
        drafts.forEach { draft ->
            val newDraft = draft.copy(id = 0)
            val draftId = draftDao.insert(newDraft.toEntity())
            foodDao.insert(newDraft.foodInfo.toEntity(draftId))
        }
    }

    /**
     * Notes the user has already reviewed or posted, newest first. Their
     * (user-edited) text is the best example of the user's own voice, so it is
     * fed back to the model as a style reference.
     */
    suspend fun getVoiceSamples(excludeId: Long, limit: Int = 3): List<String> =
        draftDao.getAll()
            .asSequence()
            .filter { it.id != excludeId }
            .filter { it.status == NoteStatus.SHARED.key || it.status == NoteStatus.REVIEWED.key }
            .mapNotNull { entity ->
                runCatching { entity.toDomain() }.getOrNull()?.selectedVariant
                    ?.let { v -> listOf(v.title, v.body).filter { it.isNotBlank() }.joinToString("\n") }
                    ?.takeIf { it.length >= 20 }
            }
            .take(limit)
            .toList()
}

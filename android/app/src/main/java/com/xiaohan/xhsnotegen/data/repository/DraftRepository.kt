package com.xiaohan.xhsnotegen.data.repository

import com.google.gson.Gson
import com.xiaohan.xhsnotegen.data.local.AppDatabase
import com.xiaohan.xhsnotegen.data.local.toDomain
import com.xiaohan.xhsnotegen.data.local.toEntity
import com.xiaohan.xhsnotegen.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DraftRepository(private val db: AppDatabase) {

    private val draftDao = db.noteDraftDao()
    private val foodDao = db.foodInfoDao()

    fun getAllFlow(): Flow<List<NoteDraft>> = draftDao.getAllFlow().map { entities ->
        entities.map { entity ->
            val foodEntity = foodDao.getByDraftId(entity.id)
            entity.toDomain(foodEntity?.toDomain())
        }
    }

    fun getByIdFlow(id: Long): Flow<NoteDraft?> = draftDao.getByIdFlow(id).map { entity ->
        entity?.let {
            val foodEntity = foodDao.getByDraftId(it.id)
            it.toDomain(foodEntity?.toDomain())
        }
    }

    suspend fun getById(id: Long): NoteDraft? {
        val entity = draftDao.getById(id) ?: return null
        val foodEntity = foodDao.getByDraftId(id)
        return entity.toDomain(foodEntity?.toDomain())
    }

    suspend fun insert(draft: NoteDraft): Long {
        val draftId = draftDao.insert(draft.toEntity())
        foodDao.insert(draft.foodInfo.toEntity(draftId))
        return draftId
    }

    suspend fun update(draft: NoteDraft) {
        draftDao.update(draft.copy(updatedAt = System.currentTimeMillis()).toEntity())
        foodDao.deleteByDraftId(draft.id)
        foodDao.insert(draft.foodInfo.toEntity(draft.id))
    }

    suspend fun updateStatus(id: Long, status: NoteStatus) {
        draftDao.updateStatus(id, status.key)
    }

    suspend fun saveGeneratedVariants(draftId: Long, variants: List<NoteVariant>) {
        val entity = draftDao.getById(draftId) ?: return
        val updated = entity.copy(
            variantsJson = Gson().toJson(variants),
            status = NoteStatus.GENERATED.key,
            updatedAt = System.currentTimeMillis(),
        )
        draftDao.update(updated)
    }

    suspend fun delete(draft: NoteDraft) {
        draftDao.delete(draft.toEntity())
    }
}

package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.NoteDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDraftDao {
    @Query("SELECT * FROM note_drafts ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<NoteDraftEntity>>

    @Query("SELECT * FROM note_drafts WHERE id = :id")
    suspend fun getById(id: Long): NoteDraftEntity?

    @Query("SELECT * FROM note_drafts WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<NoteDraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: NoteDraftEntity): Long

    @Update
    suspend fun update(draft: NoteDraftEntity)

    @Query("SELECT * FROM note_drafts ORDER BY updated_at DESC")
    suspend fun getAll(): List<NoteDraftEntity>

    @Query("DELETE FROM note_drafts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE note_drafts SET status = :status, updated_at = :now WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: String, now: Long = System.currentTimeMillis())

    @Query("SELECT id, type FROM note_drafts")
    suspend fun idsAndTypes(): List<IdAndType>

    @Query("UPDATE note_drafts SET rating = :rating WHERE id IN (:ids)")
    suspend fun setRating(ids: List<Long>, rating: Int)
}

data class IdAndType(val id: Long, val type: String)

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

    @Query("UPDATE note_drafts SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(draft: NoteDraftEntity)
}

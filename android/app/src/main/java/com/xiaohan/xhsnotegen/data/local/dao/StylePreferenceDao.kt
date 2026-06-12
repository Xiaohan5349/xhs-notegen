package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.StylePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StylePreferenceDao {
    @Query("SELECT * FROM style_preferences WHERE note_type = :noteType LIMIT 1")
    suspend fun getByNoteType(noteType: String): StylePreferenceEntity?

    @Query("SELECT * FROM style_preferences")
    fun getAllFlow(): Flow<List<StylePreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pref: StylePreferenceEntity): Long

    @Update
    suspend fun update(pref: StylePreferenceEntity)
}

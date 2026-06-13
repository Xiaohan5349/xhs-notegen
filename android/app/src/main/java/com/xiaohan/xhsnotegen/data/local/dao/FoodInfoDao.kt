package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.FoodInfoEntity

@Dao
interface FoodInfoDao {
    @Query("SELECT * FROM food_info WHERE draft_id = :draftId")
    suspend fun getByDraftId(draftId: Long): FoodInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(foodInfo: FoodInfoEntity): Long

    @Update
    suspend fun update(foodInfo: FoodInfoEntity)

    @Query("DELETE FROM food_info WHERE draft_id = :draftId")
    suspend fun deleteByDraftId(draftId: Long)

    @Query("SELECT * FROM food_info")
    suspend fun getAll(): List<FoodInfoEntity>
}

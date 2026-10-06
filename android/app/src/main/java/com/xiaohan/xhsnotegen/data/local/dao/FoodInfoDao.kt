package com.xiaohan.xhsnotegen.data.local.dao

import androidx.room.*
import com.xiaohan.xhsnotegen.data.local.entity.FoodInfoEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM food_info")
    fun getAllFlow(): Flow<List<FoodInfoEntity>>

    @Query(
        "UPDATE food_info SET country = :country, region = :region, city = :city, " +
            "latitude = :latitude, longitude = :longitude, place_source = :source, " +
            "district = :district, address = :address WHERE draft_id = :draftId"
    )
    suspend fun setPlace(
        draftId: Long, country: String?, region: String?, city: String?,
        latitude: Double?, longitude: Double?, source: String?,
        district: String?, address: String?,
    )
}

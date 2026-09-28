package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_info",
    foreignKeys = [
        ForeignKey(
            entity = NoteDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draft_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class FoodInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "draft_id") val draftId: Long,
    @ColumnInfo(name = "dish_names") val dishNames: String,
    @ColumnInfo(name = "restaurant_name") val restaurantName: String,
    @ColumnInfo(name = "location") val location: String? = null,
    @ColumnInfo(name = "meal_date") val mealDate: String? = null,
    @ColumnInfo(name = "taste_notes") val tasteNotes: String? = null,
    @ColumnInfo(name = "price_or_rating") val priceOrRating: String? = null,
    @ColumnInfo(name = "vibe_notes") val vibeNotes: String? = null,
    @ColumnInfo(name = "personal_notes") val personalNotes: String? = null,
    // Added in schema v2 (see AppDatabase.MIGRATION_1_2).
    @ColumnInfo(name = "country") val country: String? = null,
    @ColumnInfo(name = "region") val region: String? = null,
    @ColumnInfo(name = "city") val city: String? = null,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "place_source") val placeSource: String? = null,
)

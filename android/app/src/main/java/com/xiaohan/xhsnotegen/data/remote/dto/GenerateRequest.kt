package com.xiaohan.xhsnotegen.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FoodMetadataDto(
    @SerializedName("dish_names") val dishNames: String,
    @SerializedName("restaurant_name") val restaurantName: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("meal_date") val mealDate: String? = null,
    @SerializedName("taste_notes") val tasteNotes: String? = null,
    @SerializedName("price_or_rating") val priceOrRating: String? = null,
    @SerializedName("vibe_notes") val vibeNotes: String? = null,
    @SerializedName("personal_notes") val personalNotes: String? = null,
    @SerializedName("sponsored") val sponsored: Boolean = false,
)

data class GenerateRequestDto(
    @SerializedName("note_type") val noteType: String = "food",
    @SerializedName("style") val style: String,
    @SerializedName("metadata") val metadata: FoodMetadataDto,
    @SerializedName("images") val images: List<String>,
)

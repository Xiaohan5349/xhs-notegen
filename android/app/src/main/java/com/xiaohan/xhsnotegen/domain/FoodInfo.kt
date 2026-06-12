package com.xiaohan.xhsnotegen.domain

data class FoodInfo(
    val dishName: String = "",
    val restaurantName: String = "",
    val location: String = "",
    val mealDate: String = "",
    val tasteNotes: String = "",
    val priceOrRating: String = "",
    val vibeNotes: String = "",
    val personalNotes: String = "",
    val sponsored: Boolean = false,
) {
    fun isValid(): Boolean = dishName.isNotBlank() && restaurantName.isNotBlank()
}

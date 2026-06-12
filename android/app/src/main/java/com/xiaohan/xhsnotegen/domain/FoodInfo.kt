package com.xiaohan.xhsnotegen.domain

data class FoodInfo(
    val dishNames: String = "",  // one or more dishes, separated by comma/newline
    val restaurantName: String = "",
    val location: String = "",
    val mealDate: String = "",
    val tasteNotes: String = "",
    val priceOrRating: String = "",
    val vibeNotes: String = "",
    val personalNotes: String = "",
    val sponsored: Boolean = false,
) {
    fun isValid(): Boolean = dishNames.isNotBlank() && restaurantName.isNotBlank()
}

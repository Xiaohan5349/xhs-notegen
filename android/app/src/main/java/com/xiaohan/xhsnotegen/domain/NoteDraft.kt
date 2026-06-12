package com.xiaohan.xhsnotegen.domain

data class NoteDraft(
    val id: Long = 0,
    val type: NoteType = NoteType.FOOD,
    val status: NoteStatus = NoteStatus.DRAFT,
    val photoUris: List<String> = emptyList(),
    val selectedPublishPhotoUris: List<String> = emptyList(),
    val title: String = "",
    val body: String = "",
    val hashtags: List<String> = emptyList(),
    val variants: List<NoteVariant> = emptyList(),
    val selectedVariantIndex: Int = 0,
    val styleLabel: String = NoteStyle.DEFAULT.key,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val foodInfo: FoodInfo = FoodInfo(),
)

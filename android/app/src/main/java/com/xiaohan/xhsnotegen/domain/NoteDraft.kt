package com.xiaohan.xhsnotegen.domain

data class NoteDraft(
    val id: Long = 0,
    val type: NoteType = NoteType.FOOD,
    val status: NoteStatus = NoteStatus.DRAFT,
    val photoUris: List<String> = emptyList(),
    /** Photos to publish, in publish order (first = cover). Empty means "all" (legacy drafts). */
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
    val tags: List<NoteTag> = emptyList(),
    /** Your 1–5 star rating; 0 = not rated. */
    val rating: Int = 0,
) {
    val selectedVariant: NoteVariant?
        get() = variants.getOrNull(selectedVariantIndex)

    /** The photos that will actually be published, in order. */
    val publishPhotoUris: List<String>
        get() = selectedPublishPhotoUris.filter { it in photoUris }.ifEmpty { photoUris }

    val preferredStyle: NoteStyle
        get() = NoteStyle.fromLabel(styleLabel)
}

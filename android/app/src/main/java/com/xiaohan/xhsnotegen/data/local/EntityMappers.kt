package com.xiaohan.xhsnotegen.data.local

import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.local.entity.*
import com.xiaohan.xhsnotegen.domain.*

// ---- NoteDraft ----

fun NoteDraftEntity.toDomain(foodInfo: FoodInfo? = null): NoteDraft = NoteDraft(
    id = id,
    type = NoteType.fromKey(type),
    status = NoteStatus.fromKey(status),
    photoUris = JsonCodec.parseStringList(photoUris),
    selectedPublishPhotoUris = JsonCodec.parseStringList(selectedPublishPhotoUris),
    title = title ?: "",
    body = body ?: "",
    hashtags = JsonCodec.parseStringList(hashtags),
    variants = JsonCodec.parseVariants(variantsJson),
    selectedVariantIndex = selectedVariantIndex,
    styleLabel = styleLabel,
    createdAt = createdAt,
    updatedAt = updatedAt,
    foodInfo = foodInfo ?: FoodInfo(),
)

fun NoteDraft.toEntity(): NoteDraftEntity = NoteDraftEntity(
    id = id,
    type = type.key,
    status = status.key,
    photoUris = JsonCodec.toJson(photoUris),
    selectedPublishPhotoUris = JsonCodec.toJson(selectedPublishPhotoUris),
    title = title.ifBlank { null },
    body = body.ifBlank { null },
    hashtags = JsonCodec.toJson(hashtags),
    variantsJson = if (variants.isEmpty()) null else JsonCodec.toJson(variants),
    selectedVariantIndex = selectedVariantIndex,
    styleLabel = styleLabel,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---- FoodInfo ----

fun FoodInfoEntity.toDomain(): FoodInfo = FoodInfo(
    dishNames = dishNames,
    restaurantName = restaurantName,
    location = location ?: "",
    mealDate = mealDate ?: "",
    tasteNotes = tasteNotes ?: "",
    priceOrRating = priceOrRating ?: "",
    vibeNotes = vibeNotes ?: "",
    personalNotes = personalNotes ?: "",
)

fun FoodInfo.toEntity(draftId: Long): FoodInfoEntity = FoodInfoEntity(
    draftId = draftId,
    dishNames = dishNames,
    restaurantName = restaurantName,
    location = location.ifBlank { null },
    mealDate = mealDate.ifBlank { null },
    tasteNotes = tasteNotes.ifBlank { null },
    priceOrRating = priceOrRating.ifBlank { null },
    vibeNotes = vibeNotes.ifBlank { null },
    personalNotes = personalNotes.ifBlank { null },
)

// ---- StylePreference ----

fun StylePreferenceEntity.toDomain(): NoteStyle =
    NoteStyle.fromKey(preferredStyle)

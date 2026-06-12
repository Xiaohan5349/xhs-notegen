package com.xiaohan.xhsnotegen.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaohan.xhsnotegen.data.local.entity.*
import com.xiaohan.xhsnotegen.domain.*

private val gson = Gson()

// ---- NoteDraft ----

fun NoteDraftEntity.toDomain(foodInfo: FoodInfo? = null): NoteDraft = NoteDraft(
    id = id,
    type = NoteType.fromKey(type),
    status = NoteStatus.fromKey(status),
    photoUris = gson.fromJson(photoUris, StringListType) ?: emptyList(),
    selectedPublishPhotoUris = gson.fromJson(selectedPublishPhotoUris, StringListType) ?: emptyList(),
    title = title ?: "",
    body = body ?: "",
    hashtags = gson.fromJson(hashtags, StringListType) ?: emptyList(),
    variants = variantsJson?.let { gson.fromJson(it, VariantListType) } ?: emptyList(),
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
    photoUris = gson.toJson(photoUris),
    selectedPublishPhotoUris = gson.toJson(selectedPublishPhotoUris),
    title = title.ifBlank { null },
    body = body.ifBlank { null },
    hashtags = gson.toJson(hashtags),
    variantsJson = if (variants.isEmpty()) null else gson.toJson(variants),
    selectedVariantIndex = selectedVariantIndex,
    styleLabel = styleLabel,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---- FoodInfo ----

fun FoodInfoEntity.toDomain(): FoodInfo = FoodInfo(
    dishName = dishName,
    restaurantName = restaurantName,
    location = location ?: "",
    mealDate = mealDate ?: "",
    tasteNotes = tasteNotes ?: "",
    priceOrRating = priceOrRating ?: "",
    vibeNotes = vibeNotes ?: "",
    personalNotes = personalNotes ?: "",
    sponsored = sponsored,
)

fun FoodInfo.toEntity(draftId: Long): FoodInfoEntity = FoodInfoEntity(
    draftId = draftId,
    dishName = dishName,
    restaurantName = restaurantName,
    location = location.ifBlank { null },
    mealDate = mealDate.ifBlank { null },
    tasteNotes = tasteNotes.ifBlank { null },
    priceOrRating = priceOrRating.ifBlank { null },
    vibeNotes = vibeNotes.ifBlank { null },
    personalNotes = personalNotes.ifBlank { null },
    sponsored = sponsored,
)

// ---- StylePreference ----

fun StylePreferenceEntity.toDomain(): NoteStyle =
    NoteStyle.fromKey(preferredStyle)

// ---- Gson type tokens ----

private val StringListType = object : TypeToken<List<String>>() {}.type
private val VariantListType = object : TypeToken<List<NoteVariant>>() {}.type

package com.xiaohan.xhsnotegen.data.local

import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.local.entity.*
import com.xiaohan.xhsnotegen.domain.*

// ---- NoteDraft ----

fun NoteDraftEntity.toDomain(foodInfo: FoodInfo? = null): NoteDraft = NoteDraft(
    id = id,
    type = type.ifBlank { BuiltInModes.FOOD },
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
    rating = rating,
)

fun NoteDraft.toEntity(): NoteDraftEntity = NoteDraftEntity(
    id = id,
    type = type,
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
    rating = rating.coerceIn(0, 5),
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
    place = Place(
        country = country.orEmpty(),
        region = region.orEmpty(),
        city = city.orEmpty(),
        district = district.orEmpty(),
        address = address.orEmpty(),
        latitude = latitude,
        longitude = longitude,
        source = PlaceSource.fromKey(placeSource),
    ),
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
    country = place.country.ifBlank { null },
    region = place.region.ifBlank { null },
    city = place.city.ifBlank { null },
    latitude = place.latitude,
    longitude = place.longitude,
    placeSource = place.source?.key,
    district = place.district.ifBlank { null },
    address = place.address.ifBlank { null },
)

// ---- Tags ----

fun TagEntity.toDomain(): NoteTag = NoteTag(id = id, name = name, parentId = parentId)

// ---- StylePreference ----

fun StylePreferenceEntity.toDomain(): NoteStyle =
    NoteStyle.fromKey(preferredStyle)

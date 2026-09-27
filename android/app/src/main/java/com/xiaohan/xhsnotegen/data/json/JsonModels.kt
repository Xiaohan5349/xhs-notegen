package com.xiaohan.xhsnotegen.data.json

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.xiaohan.xhsnotegen.domain.*

/*
 * Wire/storage DTOs. Gson instantiates classes without calling Kotlin
 * constructors, so it ignores both default values and non-null types: a
 * missing field silently becomes null inside a "non-null" property and
 * crashes later, far from the parse. Every field here is therefore nullable,
 * and toDomain() is the single place defaults get applied.
 */

data class VariantDto(
    val styleLabel: String? = null,
    val style: String? = null, // Gemini responses use "style" (the NoteStyle key)
    val title: String? = null,
    val body: String? = null,
    val hashtags: List<String?>? = null,
    val warnings: List<String?>? = null,
) {
    fun toDomain(fallbackStyle: NoteStyle? = null): NoteVariant {
        val resolved = NoteStyle.fromLabelOrNull(style)
            ?: NoteStyle.fromLabelOrNull(styleLabel)
            ?: fallbackStyle
            ?: NoteStyle.DEFAULT
        return NoteVariant(
            styleLabel = resolved.key,
            title = title.orEmpty().trim(),
            body = body.orEmpty().trim(),
            hashtags = normalizeHashtags(hashtags.orEmpty().filterNotNull()),
            warnings = warnings.orEmpty().filterNotNull().filter { it.isNotBlank() },
        )
    }
}

data class FoodInfoDto(
    val dishNames: String? = null,
    val restaurantName: String? = null,
    val location: String? = null,
    val mealDate: String? = null,
    val tasteNotes: String? = null,
    val priceOrRating: String? = null,
    val vibeNotes: String? = null,
    val personalNotes: String? = null,
) {
    fun toDomain() = FoodInfo(
        dishNames = dishNames.orEmpty(),
        restaurantName = restaurantName.orEmpty(),
        location = location.orEmpty(),
        mealDate = mealDate.orEmpty(),
        tasteNotes = tasteNotes.orEmpty(),
        priceOrRating = priceOrRating.orEmpty(),
        vibeNotes = vibeNotes.orEmpty(),
        personalNotes = personalNotes.orEmpty(),
    )
}

/** One draft as found in an export file (field names match NoteDraft). */
data class DraftDto(
    val type: String? = null,
    val status: String? = null,
    val photoUris: List<String?>? = null,
    val selectedPublishPhotoUris: List<String?>? = null,
    val title: String? = null,
    val body: String? = null,
    val hashtags: List<String?>? = null,
    val variants: List<VariantDto?>? = null,
    val selectedVariantIndex: Int? = null,
    val styleLabel: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val foodInfo: FoodInfoDto? = null,
) {
    fun toDomain(): NoteDraft {
        val now = System.currentTimeMillis()
        val variantList = variants.orEmpty().filterNotNull().map { it.toDomain() }
        return NoteDraft(
            id = 0,
            // Exports store enum *names* (Gson default), older data may hold keys.
            type = NoteType.entries.firstOrNull { it.name == type } ?: NoteType.fromKey(type.orEmpty()),
            status = NoteStatus.entries.firstOrNull { it.name == status } ?: NoteStatus.fromKey(status.orEmpty()),
            photoUris = photoUris.orEmpty().filterNotNull(),
            selectedPublishPhotoUris = selectedPublishPhotoUris.orEmpty().filterNotNull(),
            title = title.orEmpty(),
            body = body.orEmpty(),
            hashtags = hashtags.orEmpty().filterNotNull(),
            variants = variantList,
            selectedVariantIndex = (selectedVariantIndex ?: 0)
                .coerceIn(0, (variantList.size - 1).coerceAtLeast(0)),
            styleLabel = NoteStyle.fromLabel(styleLabel.orEmpty()).key,
            createdAt = createdAt ?: now,
            updatedAt = updatedAt ?: now,
            foodInfo = foodInfo?.toDomain() ?: FoodInfo(),
        )
    }
}

/** Top-level shape of a model's answer: {"variants":[...]} */
class VariantsResponse(val variants: List<VariantDto?>? = null)

data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val drafts: List<NoteDraft> = emptyList(),
)

data class ImportData(
    val version: Int? = null,
    val drafts: List<DraftDto?>? = null,
)

object JsonCodec {
    val gson: Gson = Gson()

    private val stringListType = object : TypeToken<List<String?>>() {}.type
    private val variantListType = object : TypeToken<List<VariantDto?>>() {}.type

    fun toJson(value: Any?): String = gson.toJson(value)

    fun parseStringList(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList()
        else gson.fromJson<List<String?>>(json, stringListType).orEmpty().filterNotNull()

    fun parseVariants(json: String?): List<NoteVariant> =
        if (json.isNullOrBlank()) emptyList()
        else gson.fromJson<List<VariantDto?>>(json, variantListType).orEmpty()
            .filterNotNull().map { it.toDomain() }

    fun parseImport(json: String): ImportData =
        gson.fromJson(json, ImportData::class.java) ?: throw JsonParseException("Empty file")
}

/** "#上海美食 " / "上海美食" → "上海美食"; drops blanks and duplicates. */
fun normalizeHashtags(raw: List<String>): List<String> =
    raw.flatMap { it.split(Regex("[\\s#＃]+")) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

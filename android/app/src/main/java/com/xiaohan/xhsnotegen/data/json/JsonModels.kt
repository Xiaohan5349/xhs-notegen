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
    val model: String? = null,
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
            model = model.orEmpty(),
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
    val place: PlaceDto? = null,
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
        place = place?.toDomain() ?: Place(),
    )
}

data class PlaceDto(
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    val district: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String? = null,
) {
    fun toDomain() = Place(
        country = country.orEmpty(), region = region.orEmpty(), city = city.orEmpty(),
        district = district.orEmpty(), address = address.orEmpty(),
        latitude = latitude, longitude = longitude,
        // Exports store the enum name; tolerate the key too.
        source = PlaceSource.entries.firstOrNull { it.name == source } ?: PlaceSource.fromKey(source),
    )
}

data class TagDto(val name: String? = null)

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
    val tags: List<TagDto?>? = null,
    val rating: Int? = null,
) {
    fun toDomain(): NoteDraft {
        val now = System.currentTimeMillis()
        val variantList = variants.orEmpty().filterNotNull().map { it.toDomain() }
        return NoteDraft(
            id = 0,
            // Exports store enum *names* (Gson default), older data may hold keys.
            // Older exports stored the enum name "FOOD"; now it's the mode key.
            type = type?.lowercase()?.takeIf { it.isNotBlank() } ?: BuiltInModes.FOOD,
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
            tags = tags.orEmpty().mapNotNull { it?.name?.trim()?.takeIf { n -> n.isNotEmpty() } }
                .distinct().map { NoteTag(name = it) },
            rating = (rating ?: 0).coerceIn(0, 5),
        )
    }
}

/** Top-level shape of a model's answer: {"variants":[...]} */
class VariantsResponse(val variants: List<VariantDto?>? = null)

data class ExportData(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val drafts: List<NoteDraft> = emptyList(),
    /** Tag hierarchy by name: sub-tag → its root tag. */
    val tagParents: Map<String, String> = emptyMap(),
)

data class ImportData(
    val version: Int? = null,
    val drafts: List<DraftDto?>? = null,
    val tagParents: Map<String?, String?>? = null,
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

/** Stored writing mode; nullable so a partial or older record still loads with defaults. */
data class ModeDto(
    val key: String? = null,
    val name: String? = null,
    val rootTag: String? = null,
    val promptHeading: String? = null,
    val fields: Map<String, FieldSpecDto?>? = null,
    val instructions: String? = null,
    val styles: Map<String, String?>? = null,
) {
    /** Fills anything missing from [base] (the built-in default, or a blank custom mode). */
    fun toDomain(base: WritingMode): WritingMode = base.copy(
        name = name?.takeIf { it.isNotBlank() } ?: base.name,
        rootTag = rootTag?.trim() ?: base.rootTag,
        promptHeading = promptHeading?.takeIf { it.isNotBlank() } ?: base.promptHeading,
        fields = FieldSlot.entries.associateWith { slot ->
            val d = fields?.get(slot.name)
            val b = base.field(slot)
            FieldSpec(
                label = d?.label?.takeIf { it.isNotBlank() } ?: b.label,
                hint = d?.hint ?: b.hint,
                promptKey = d?.promptKey?.takeIf { it.isNotBlank() } ?: b.promptKey,
            )
        },
        instructions = instructions?.takeIf { it.isNotBlank() } ?: base.instructions,
        styles = NoteStyle.entries.associate { s -> s.key to (styles?.get(s.key)?.takeIf { it.isNotBlank() } ?: base.style(s)) },
    )

    companion object {
        fun from(m: WritingMode) = ModeDto(
            key = m.key, name = m.name, rootTag = m.rootTag, promptHeading = m.promptHeading,
            fields = m.fields.mapKeys { it.key.name }.mapValues { FieldSpecDto(it.value.label, it.value.hint, it.value.promptKey) },
            instructions = m.instructions, styles = m.styles,
        )
    }
}

data class FieldSpecDto(val label: String? = null, val hint: String? = null, val promptKey: String? = null)

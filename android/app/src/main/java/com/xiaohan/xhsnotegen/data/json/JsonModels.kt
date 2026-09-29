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
    /** Exports store the enum name ("ZH"); accept the key ("zh") too. */
    val language: String? = null,
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
            language = PromptLanguage.entries.firstOrNull { it.name.equals(language, ignoreCase = true) || it.key == language },
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

/**
 * Stored writing mode; nullable so a partial or older record still loads with defaults.
 * The top-level prompt fields hold the Chinese prompt (the only one before v1.8);
 * [en] holds the English one. Built-in modes are stored as differences from their
 * defaults, so untouched parts follow the app language and pick up better defaults.
 */
data class ModeDto(
    val key: String? = null,
    val name: String? = null,
    /** Not used since v1.10 (modes no longer add a tag); read only to clean up the tags it made. */
    val rootTag: String? = null,
    val promptHeading: String? = null,
    val fields: Map<String, FieldSpecDto?>? = null,
    val instructions: String? = null,
    val styles: Map<String, String?>? = null,
    val en: PromptSetDto? = null,
    val language: String? = null,
    val maxPhotos: Int? = null,
) {
    /**
     * Fills anything missing from [base] (the built-in default, or a blank custom
     * mode). A name or label equal to the default in [other] (the same default in
     * the other app language) counts as not customized, so it follows the app language.
     */
    fun toDomain(base: WritingMode, other: WritingMode? = null): WritingMode {
        fun localized(stored: String?, baseValue: String, otherValue: String?): String {
            val v = stored?.takeIf { it.isNotBlank() } ?: return baseValue
            return if (v == otherValue) baseValue else v
        }
        val zhBase = base.prompts[PromptLanguage.ZH] ?: BuiltInModes.genericPrompt(PromptLanguage.ZH)
        val zh = PromptSet(
            heading = promptHeading?.takeIf { it.isNotBlank() } ?: zhBase.heading,
            fields = FieldSlot.entries.associateWith { slot ->
                val d = fields?.get(slot.name)
                val b = zhBase.fields[slot] ?: BuiltInModes.genericPromptField(slot, PromptLanguage.ZH)
                PromptField(key = d?.promptKey?.takeIf { it.isNotBlank() } ?: b.key, hint = d?.hint ?: b.hint)
            },
            instructions = instructions?.takeIf { it.isNotBlank() } ?: zhBase.instructions,
            styles = NoteStyle.entries.associate { s -> s.key to (styles?.get(s.key)?.takeIf { it.isNotBlank() } ?: zhBase.styles[s.key].orEmpty()) },
        )
        val enBase = base.prompts[PromptLanguage.EN] ?: BuiltInModes.genericPrompt(PromptLanguage.EN)
        return base.copy(
            name = if (base.builtIn) localized(name, base.name, other?.name) else name?.takeIf { it.isNotBlank() } ?: base.name,
            labels = FieldSlot.entries.associateWith { slot ->
                localized(fields?.get(slot.name)?.label, base.field(slot).label, other?.field(slot)?.label)
            },
            prompts = mapOf(PromptLanguage.ZH to zh, PromptLanguage.EN to (en?.toDomain(enBase) ?: enBase)),
            language = PromptLanguage.fromKey(language) ?: base.language,
            maxPhotos = (maxPhotos?.takeIf { it != WritingMode.OLD_DEFAULT_MAX_PHOTOS } ?: base.maxPhotos)
                .coerceIn(WritingMode.MIN_PHOTOS, WritingMode.MAX_PHOTOS_LIMIT),
        )
    }

    companion object {
        /**
         * What to store for [m]. With [base] (the default in the current app language)
         * and [other] (the same default in the other language), only differences are
         * kept; without them (custom modes) everything is.
         */
        fun from(m: WritingMode, base: WritingMode? = null, other: WritingMode? = null): ModeDto {
            fun changed(v: String, vararg defaults: String?) = v.takeIf { base == null || v !in defaults }
            val zh = m.prompts[PromptLanguage.ZH]?.takeIf { base == null || it != base.prompts[PromptLanguage.ZH] }
            val en = m.prompts[PromptLanguage.EN]?.takeIf { base == null || it != base.prompts[PromptLanguage.EN] }
            return ModeDto(
                key = m.key,
                name = if (base == null || !m.builtIn) m.name else changed(m.name, base.name, other?.name),
                promptHeading = zh?.heading,
                fields = FieldSlot.entries.associate { slot ->
                    slot.name to FieldSpecDto(
                        label = changed(m.field(slot).label, base?.field(slot)?.label, other?.field(slot)?.label),
                        hint = zh?.fields?.get(slot)?.hint,
                        promptKey = zh?.fields?.get(slot)?.key,
                    )
                },
                instructions = zh?.instructions,
                styles = zh?.styles,
                en = en?.let { PromptSetDto.from(it) },
                language = m.language.key,
                maxPhotos = m.maxPhotos.takeIf { base == null || it != base.maxPhotos },
            )
        }
    }
}

data class FieldSpecDto(val label: String? = null, val hint: String? = null, val promptKey: String? = null)

data class PromptSetDto(
    val heading: String? = null,
    val fields: Map<String, PromptFieldDto?>? = null,
    val instructions: String? = null,
    val styles: Map<String, String?>? = null,
) {
    fun toDomain(base: PromptSet) = PromptSet(
        heading = heading?.takeIf { it.isNotBlank() } ?: base.heading,
        fields = FieldSlot.entries.associateWith { slot ->
            val d = fields?.get(slot.name)
            val b = base.fields[slot] ?: PromptField("", "")
            PromptField(key = d?.key?.takeIf { it.isNotBlank() } ?: b.key, hint = d?.hint ?: b.hint)
        },
        instructions = instructions?.takeIf { it.isNotBlank() } ?: base.instructions,
        styles = NoteStyle.entries.associate { s -> s.key to (styles?.get(s.key)?.takeIf { it.isNotBlank() } ?: base.styles[s.key].orEmpty()) },
    )

    companion object {
        fun from(p: PromptSet) = PromptSetDto(
            heading = p.heading,
            fields = p.fields.mapKeys { it.key.name }.mapValues { PromptFieldDto(it.value.key, it.value.hint) },
            instructions = p.instructions,
            styles = p.styles,
        )
    }
}

data class PromptFieldDto(val key: String? = null, val hint: String? = null)

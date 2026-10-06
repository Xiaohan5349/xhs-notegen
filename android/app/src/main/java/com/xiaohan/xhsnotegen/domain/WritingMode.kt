package com.xiaohan.xhsnotegen.domain

import com.xiaohan.xhsnotegen.i18n.LanguageStore

/** The six inputs of the create form. Stored in FoodInfo's columns whatever the mode. */
enum class FieldSlot { SUBJECT, PLACE, FEELING, COST, SCENE, OTHER }

/**
 * How one form field looks in a mode (a read-only view for the active note language).
 * @param label shown in the form ("What did you eat")
 * @param hint placeholder text, an example in the note's language
 * @param promptKey how the field is named for the AI ("菜" / "Dishes")
 */
data class FieldSpec(val label: String, val hint: String, val promptKey: String)

/** The language a mode's prompt is written in — and so the language of its notes. */
enum class PromptLanguage(val key: String, val label: String) {
    ZH("zh", "中文"), EN("en", "English");

    companion object {
        fun fromKey(key: String?): PromptLanguage? = entries.firstOrNull { it.key == key }
    }
}

/** How a fact is named for the AI, and the example shown in the empty field. */
data class PromptField(val key: String, val hint: String)

/** Everything the AI sees for one mode in one language. */
data class PromptSet(
    /** Heading above the facts in the AI prompt, e.g. "这次吃的". */
    val heading: String,
    val fields: Map<FieldSlot, PromptField>,
    /** Main AI instructions (the fixed JSON output rules are appended automatically). */
    val instructions: String,
    /** Per-style instructions, keyed by NoteStyle.key. */
    val styles: Map<String, String>,
)

/**
 * A kind of note — food, travel, or one you define. Each mode has its own
 * form labels, AI instructions in Chinese and English (one of them in use)
 * and a photo limit. Notes are grouped by mode on the home screen.
 */
data class WritingMode(
    val key: String,
    val name: String,
    /** Form labels, in the app's language. */
    val labels: Map<FieldSlot, String>,
    val prompts: Map<PromptLanguage, PromptSet>,
    val language: PromptLanguage = PromptLanguage.ZH,
    /** Most photos a note in this mode can have. */
    val maxPhotos: Int = DEFAULT_MAX_PHOTOS,
    val builtIn: Boolean = false,
) {
    /** The prompt in use. */
    val prompt: PromptSet
        get() = prompts[language] ?: prompts[PromptLanguage.ZH] ?: BuiltInModes.genericPrompt(language)

    val instructions: String get() = prompt.instructions
    val promptHeading: String get() = prompt.heading
    val styles: Map<String, String> get() = prompt.styles

    /**
     * Label and hint follow the app language (they're for you); the name given to the AI follows
     * the note language (it's part of the prompt).
     */
    fun field(slot: FieldSlot): FieldSpec {
        val p = prompt.fields[slot] ?: BuiltInModes.genericPromptField(slot, language)
        val appLanguage = if (LanguageStore.isZh) PromptLanguage.ZH else PromptLanguage.EN
        val hint = prompts[appLanguage]?.fields?.get(slot)?.hint?.takeIf { it.isNotBlank() } ?: p.hint
        return FieldSpec(labels[slot] ?: BuiltInModes.genericLabel(slot), hint, p.key.ifBlank { BuiltInModes.genericPromptField(slot, language).key })
    }

    fun style(style: NoteStyle): String = styles[style.key]?.takeIf { it.isNotBlank() } ?: BuiltInModes.genericStyle(style, language)

    /** A copy with the prompt in use replaced. */
    fun withPrompt(p: PromptSet): WritingMode = copy(prompts = prompts + (language to p))

    companion object {
        const val DEFAULT_MAX_PHOTOS = 40
        const val MIN_PHOTOS = 1
        /** Upper bound for any mode's limit. */
        const val MAX_PHOTOS_LIMIT = 40
        /** Before v1.10 every mode stored 20 when edited; that means "not set". */
        const val OLD_DEFAULT_MAX_PHOTOS = 20
    }
}

package com.xiaohan.xhsnotegen.domain

/** The six inputs of the create form. Stored in FoodInfo's columns whatever the mode. */
enum class FieldSlot { SUBJECT, PLACE, FEELING, COST, SCENE, OTHER }

/**
 * How one form field looks in a mode.
 * @param label shown in the form ("What did you eat")
 * @param hint placeholder text
 * @param promptKey how the field is named for the AI (Chinese, e.g. "菜")
 */
data class FieldSpec(val label: String, val hint: String, val promptKey: String)

/**
 * A kind of note — food, travel, or one you define. Each mode has its own
 * form labels, AI instructions and style descriptions, and a root tag that
 * every note written in it gets (the "upper" tag other tags can sit under).
 */
data class WritingMode(
    val key: String,
    val name: String,
    val rootTag: String,
    /** Heading above the facts in the AI prompt, e.g. "这次吃的". */
    val promptHeading: String,
    val fields: Map<FieldSlot, FieldSpec>,
    /** Main AI instructions (the fixed JSON output rules are appended automatically). */
    val instructions: String,
    /** Per-style instructions, keyed by NoteStyle.key. */
    val styles: Map<String, String>,
    val builtIn: Boolean = false,
) {
    fun field(slot: FieldSlot): FieldSpec = fields[slot] ?: BuiltInModes.genericField(slot)
    fun style(style: NoteStyle): String = styles[style.key]?.takeIf { it.isNotBlank() } ?: BuiltInModes.genericStyle(style)
}

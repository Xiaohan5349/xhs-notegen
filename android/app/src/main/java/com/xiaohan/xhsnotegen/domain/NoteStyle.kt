package com.xiaohan.xhsnotegen.domain

/** Food note styles. Key values match backend StyleLabel. */
enum class NoteStyle(val key: String, val displayName: String) {
    CASUAL_STORY("casual_story", "Casual Story"),
    PRACTICAL("practical", "Practical"),
    PUNCHY("punchy", "XHS Punchy"),
    CLEAN("clean", "Clean/Minimal");

    companion object {
        val DEFAULT = CASUAL_STORY

        fun fromKey(key: String): NoteStyle =
            entries.firstOrNull { it.key == key } ?: DEFAULT

        /** Resolve preferred style: per-type → global → hardcoded fallback. */
        fun resolve(
            typePreference: NoteStyle?,
            globalPreference: NoteStyle?,
        ): NoteStyle = typePreference ?: globalPreference ?: DEFAULT
    }
}

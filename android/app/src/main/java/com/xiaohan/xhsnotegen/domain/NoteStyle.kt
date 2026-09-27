package com.xiaohan.xhsnotegen.domain

/** Food note styles. [key] is what gets stored and sent to the model. */
enum class NoteStyle(val key: String, val displayName: String, val blurb: String) {
    CASUAL_STORY("casual_story", "Casual Story", "Chatty, like texting a friend"),
    PRACTICAL("practical", "Practical", "What you ordered, price, go again?"),
    PUNCHY("punchy", "XHS Punchy", "Short labeled lines, easy to scan"),
    CLEAN("clean", "Clean/Minimal", "A few plain sentences");

    companion object {
        val DEFAULT = CASUAL_STORY

        fun fromKey(key: String): NoteStyle =
            entries.firstOrNull { it.key == key } ?: DEFAULT

        /**
         * Accepts a key ("practical"), a display name ("Practical") or an enum
         * name. Older drafts stored whatever label the model returned, so the
         * lookup is lenient on case and whitespace.
         */
        fun fromLabelOrNull(label: String?): NoteStyle? {
            val l = label?.trim()?.lowercase() ?: return null
            if (l.isEmpty()) return null
            return entries.firstOrNull {
                it.key == l || it.displayName.lowercase() == l || it.name.lowercase() == l
            } ?: entries.firstOrNull { l.contains(it.key.substringBefore('_')) }
        }

        fun fromLabel(label: String): NoteStyle = fromLabelOrNull(label) ?: DEFAULT

        /** Preferred style first, then the rest in declaration order. */
        fun orderedFrom(preferred: NoteStyle): List<NoteStyle> =
            listOf(preferred) + entries.filter { it != preferred }

        /** Resolve preferred style: per-type → global → hardcoded fallback. */
        fun resolve(
            typePreference: NoteStyle?,
            globalPreference: NoteStyle?,
        ): NoteStyle = typePreference ?: globalPreference ?: DEFAULT
    }
}

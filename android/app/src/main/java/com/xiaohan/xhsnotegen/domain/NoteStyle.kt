package com.xiaohan.xhsnotegen.domain

import com.xiaohan.xhsnotegen.i18n.tr

/** Food note styles. [key] is what gets stored and sent to the model. */
enum class NoteStyle(val key: String, private val englishName: String) {
    CASUAL_STORY("casual_story", "Casual Story"),
    PRACTICAL("practical", "Practical"),
    PUNCHY("punchy", "XHS Punchy"),
    CLEAN("clean", "Clean/Minimal");

    /** Shown in the UI, in the app language. */
    val displayName: String
        get() = when (this) {
            CASUAL_STORY -> tr(englishName, "随手记")
            PRACTICAL -> tr(englishName, "实用记录")
            PUNCHY -> tr(englishName, "清单速记")
            CLEAN -> tr(englishName, "极简")
        }

    val blurb: String
        get() = when (this) {
            CASUAL_STORY -> tr("Chatty, like texting a friend", "像给朋友发消息一样聊")
            PRACTICAL -> tr("What you ordered, price, go again?", "点了啥、多少钱、还会再去吗？")
            PUNCHY -> tr("Short labeled lines, easy to scan", "分点短句，一眼扫完")
            CLEAN -> tr("A few plain sentences", "几句平实的话")
        }

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
                it.key == l || it.englishName.lowercase() == l || it.name.lowercase() == l
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

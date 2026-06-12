package com.xiaohan.xhsnotegen.domain

enum class NoteStatus(val key: String) {
    DRAFT("draft"),
    GENERATED("generated"),
    REVIEWED("reviewed"),
    SHARED("shared");

    companion object {
        fun fromKey(key: String): NoteStatus =
            entries.firstOrNull { it.key == key } ?: DRAFT
    }
}

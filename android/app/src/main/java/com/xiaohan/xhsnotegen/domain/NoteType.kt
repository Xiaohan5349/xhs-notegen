package com.xiaohan.xhsnotegen.domain

/** Open-ended note type. v1 only uses FOOD; others are designed for future expansion. */
enum class NoteType(val key: String) {
    FOOD("food");

    companion object {
        fun fromKey(key: String): NoteType =
            entries.firstOrNull { it.key == key } ?: FOOD
    }
}

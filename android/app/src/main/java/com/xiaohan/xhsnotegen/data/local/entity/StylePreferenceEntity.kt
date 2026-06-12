package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "style_preferences")
data class StylePreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_type") val noteType: String,  // "all" = global, "food" = food-specific
    @ColumnInfo(name = "preferred_style") val preferredStyle: String,
)

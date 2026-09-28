package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true), Index(value = ["parent_id"])],
    foreignKeys = [
        // Deleting a root tag keeps its sub-tags, as top-level tags.
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["parent_id"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    /** Added in schema v4: the root (upper) tag this tag sits under, or null. */
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
)

/** Many-to-many link between notes and tags; removed automatically with either side. */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["draft_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = NoteDraftEntity::class, parentColumns = ["id"], childColumns = ["draft_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class NoteTagEntity(
    @ColumnInfo(name = "draft_id") val draftId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

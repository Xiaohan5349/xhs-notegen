package com.xiaohan.xhsnotegen.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteStatus
import com.xiaohan.xhsnotegen.domain.NoteType

@Entity(tableName = "note_drafts")
data class NoteDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String = NoteType.FOOD.key,
    @ColumnInfo(name = "status") val status: String = NoteStatus.DRAFT.key,
    @ColumnInfo(name = "photo_uris") val photoUris: String = "[]",
    @ColumnInfo(name = "selected_publish_photo_uris") val selectedPublishPhotoUris: String = "[]",
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "body") val body: String? = null,
    @ColumnInfo(name = "hashtags") val hashtags: String? = null,
    @ColumnInfo(name = "variants_json") val variantsJson: String? = null,
    @ColumnInfo(name = "selected_variant_index") val selectedVariantIndex: Int = 0,
    @ColumnInfo(name = "style_label") val styleLabel: String = NoteStyle.DEFAULT.key,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
